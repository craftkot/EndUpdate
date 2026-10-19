package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Two separate server-side hunting behaviours on VANILLA chorus_flower blocks.
 * The model V17 is a reference rig, not the game's final model.
 * Attack-specific poses must later be implemented by a procedural client renderer.
 */
public final class MimicAwakening {
    private static final int SEARCH_XZ = 5;
    private static final int SEARCH_Y = 6;
    private static final double PLAYER_WAKE_SQR = 4.5 * 4.5;
    // Tall chorus flowers must perceive prey on the ground below them.
    // Rectangular scan bounds plus a radial cap avoid unrealistic infinite reach.
    public static final double MITE_HUNT_XZ = 18.0;
    public static final double MITE_HUNT_VERTICAL = 24.0;
    private static final double MITE_HUNT_RADIUS_SQR = 30.0 * 30.0;

    private static boolean withinMiteHuntRange(BlockPos flower, Entity mite) {
        Vec3 at = Vec3.atCenterOf(flower);
        double dx = at.x - mite.getX(), dz = at.z - mite.getZ();
        double dy = at.y - (mite.getY() + mite.getBbHeight() * .5);
        return dx * dx + dz * dz <= MITE_HUNT_XZ * MITE_HUNT_XZ
            && Math.abs(dy) <= MITE_HUNT_VERTICAL
            && dx * dx + dy * dy + dz * dz <= MITE_HUNT_RADIUS_SQR;
    }
    private static final int PLAYER_CHARGE_TICKS = 35;
    // A single successful bite cannot empty a full inventory at once.
    private static final int PLAYER_PEARLS_STOLEN_PER_BITE = 3;
    private static final int MITE_CHARGE_TICKS = 26;
    private static final int DIGEST_TICKS = 100; // 5 seconds at 20 TPS
    private static final int RECOVER_TICKS = 18;

    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    /** Animation names are intentionally distinct, not aliases of the same bite. */
    public enum HuntType {
        PLAYER_PEARL("bite_player_high"),
        ENDERMITE("eat_endermite_low");

        public final String animation;

        HuntType(String animation) {
            this.animation = animation;
        }
    }

    private record Strike(UUID targetId, HuntType kind, long startedAt, long hitAt, long clearAt) { }
    private record Candidate(BlockPos flower, Entity target, HuntType type) { }

    private static final class State {
        final Map<Long, Strike> charging = new HashMap<>();
        // Actual vanilla blocks belonging to active appendages, never shared junctions.
        final Map<Long, Set<Long>> movingParts = new HashMap<>();
        final Set<Long> successfulHunts = new HashSet<>();
        final Map<Long, Long> flowerCooldown = new HashMap<>();
        final Map<UUID, Long> targetCooldown = new HashMap<>();
        // Only a flower that actually swallowed a mite produces this effect.
        final Map<Long, Long> digestingUntil = new HashMap<>();
        int miteScanCursor = 0;
    }

    private MimicAwakening() { }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new State());
    }

    public static int activeCount(ServerLevel level) {
        return state(level).charging.size();
    }

    /** Used by the vanilla block-shape hook on the logical server. */
    public static boolean isMovingPart(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        for (Set<Long> parts : state(level).movingParts.values())
            if (parts.contains(key)) return true;
        return false;
    }

    /** The moving mask follows the same root-exclusive ownership as client rendering. */
    private static void holdMovingParts(ServerLevel level, BlockPos flower) {
        List<BlockPos> path = MimicBranchPath.trace(level, flower);
        Set<Long> owned = new HashSet<>();
        for (int i = 1; i < path.size(); i++) owned.add(path.get(i).asLong());
        owned.add(flower.asLong());
        state(level).movingParts.put(flower.asLong(), Set.copyOf(owned));
    }

    private static void releaseMovingParts(ServerLevel level, BlockPos flower) {
        state(level).movingParts.remove(flower.asLong());
    }

    public static boolean isSleeping(ServerLevel level, BlockPos flower) {
        // Cooldown is not an animation: dormant flowers are vulnerable between attacks.
        return !state(level).charging.containsKey(flower.asLong());
    }

    public static void onFlowerDestroyed(ServerLevel level, BlockPos flower) {
        State state = state(level);
        long id = flower.asLong();
        state.charging.remove(id);
        state.movingParts.remove(id);
        state.successfulHunts.remove(id);
        state.flowerCooldown.remove(id);
        state.digestingUntil.remove(id);
        notifyPose(level, flower, null, (byte) 0, MimicPosePacket.STOP);
    }

    public static String activeAnimation(ServerLevel level, BlockPos pos) {
        Strike strike = state(level).charging.get(pos.asLong());
        return strike == null ? "idle_mimic" : strike.kind().animation;
    }

    public static MimicProfile profile(ServerLevel level, BlockPos pos) {
        return MimicProfile.forFlower(level.getSeed(), pos);
    }

    /**
     * Called near players twice per second. Endermites are selected first:
     * they are actual prey. A player is only bait when carrying an ender pearl.
     */
    public static void seek(ServerLevel level, ServerPlayer nearbyPlayer) {
        if (nearbyPlayer.isSpectator()) return;
        State state = state(level);
        long now = level.getGameTime();
        BlockPos center = nearbyPlayer.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        MimicData data = MimicData.get(level);

        AABB search = AABB.ofSize(nearbyPlayer.position(), 2 * SEARCH_XZ + 2, 2 * SEARCH_Y + 2, 2 * SEARCH_XZ + 2);
        List<Endermite> mites = level.getEntitiesOfClass(Endermite.class, search, Entity::isAlive);
        List<Candidate> miteOptions = new ArrayList<>();
        List<Candidate> playerOptions = new ArrayList<>();

        for (int x = center.getX() - SEARCH_XZ; x <= center.getX() + SEARCH_XZ; x++) {
            for (int z = center.getZ() - SEARCH_XZ; z <= center.getZ() + SEARCH_XZ; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = Math.max(level.getMinY(), center.getY() - SEARCH_Y);
                     y <= Math.min(level.getMaxY() - 1, center.getY() + SEARCH_Y); y++) {
                    cursor.set(x, y, z);
                    long key = cursor.asLong();
                    if (!data.isInfected(cursor)) continue;
                    if (state.charging.containsKey(key) || now < state.flowerCooldown.getOrDefault(key, 0L)) continue;
                    if (!level.getBlockState(cursor).is(Blocks.CHORUS_FLOWER)) {
                        data.reconcileFlower(level, cursor);
                        continue;
                    }
                    BlockPos flower = cursor.immutable();
                    Vec3 source = Vec3.atCenterOf(flower);
                    for (Endermite mite : mites) {
                        if (now < state.targetCooldown.getOrDefault(mite.getUUID(), 0L)) continue;
                        if (withinMiteHuntRange(flower, mite)
                            && canReachMite(level, flower, mite)
                            && unobstructed(level, flower, mite)) {
                            miteOptions.add(new Candidate(flower, mite, HuntType.ENDERMITE));
                        }
                    }
                    if (hasEnderPearl(nearbyPlayer) && canHuntPlayer(nearbyPlayer)
                        && now >= state.targetCooldown.getOrDefault(nearbyPlayer.getUUID(), 0L)
                        && source.distanceToSqr(nearbyPlayer.position()) <= PLAYER_WAKE_SQR
                        && unobstructed(level, flower, nearbyPlayer)) {
                        playerOptions.add(new Candidate(flower, nearbyPlayer, HuntType.PLAYER_PEARL));
                    }
                }
            }
        }

        List<Candidate> options = miteOptions.isEmpty() ? playerOptions : miteOptions;
        if (!options.isEmpty()) {
            Candidate chosen = options.get(level.getRandom().nextInt(options.size()));
            begin(level, chosen.flower(), chosen.target(), chosen.type(), false);
        }
    }

    public static boolean provoke(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!MimicData.get(level).isInfected(pos)) return false;
        // Operator test chooses an actual endermite first if close enough.
        AABB area = AABB.ofSize(Vec3.atCenterOf(pos),
            MITE_HUNT_XZ * 2 + 2, MITE_HUNT_VERTICAL * 2 + 2, MITE_HUNT_XZ * 2 + 2);
        for (Endermite mite : level.getEntitiesOfClass(Endermite.class, area, Entity::isAlive)) {
            if (withinMiteHuntRange(pos, mite)) {
                return begin(level, pos, mite, HuntType.ENDERMITE, true);
            }
        }
        if (!hasEnderPearl(player)) return false;
        return begin(level, pos, player, HuntType.PLAYER_PEARL, true);
    }

    private static boolean canHuntPlayer(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    private static boolean hasEnderPearl(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) return true;
        }
        return false;
    }

    /**
     * Server-authoritative: remove up to three real ender pearls, not a
     * duplicate cosmetic effect. Search every player-inventory slot,
     * including the hotbar and offhand, even if the stacks are split.
     * This is invoked ONLY at contact on a successful player bite.
     */
    private static int stealPlayerPearls(ServerPlayer player) {
        var inventory = player.getInventory();
        int remaining = PLAYER_PEARLS_STOLEN_PER_BITE;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.is(Items.ENDER_PEARL)) continue;
            int amount = Math.min(remaining, stack.getCount());
            if (amount <= 0) continue;
            stack.shrink(amount);
            remaining -= amount;
        }
        int stolen = PLAYER_PEARLS_STOLEN_PER_BITE - remaining;
        if (stolen > 0) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
        return stolen;
    }

    private static boolean begin(ServerLevel level, BlockPos pos, Entity target,
                                 HuntType kind, boolean force) {
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            MimicData.get(level).reconcileFlower(level, pos);
            return false;
        }
        State state = state(level);
        long key = pos.asLong();
        long now = level.getGameTime();
        if (state.charging.containsKey(key)) return false;
        // Do not animate an impossible reach: this was the other reason
        // tall flowers grew absurdly long boom-like cranes.
        if (kind == HuntType.ENDERMITE && !canReachMite(level, pos, target))
            return false;
        if (!force && (now < state.flowerCooldown.getOrDefault(key, 0L)
            || now < state.targetCooldown.getOrDefault(target.getUUID(), 0L))) return false;

        // The branch descends while turning from the beginning of the bite.
        // Farther prey get a proportionate (but not excessive) travel time.
        int charge = kind == HuntType.ENDERMITE
            ? MITE_CHARGE_TICKS + Math.min(64, (int)Math.ceil(
                Math.max(0.0,
                    Math.abs(target.getX() - (pos.getX() + .5))
                    + Math.abs(target.getZ() - (pos.getZ() + .5))
                    + Math.abs(target.getY() - (pos.getY() + .5)) - 5.0) * 1.15))
            : PLAYER_CHARGE_TICKS;
        state.charging.put(key, new Strike(target.getUUID(), kind, now, now + charge,
            now + charge + RECOVER_TICKS));
        holdMovingParts(level, pos);
        // A pearl carrier remains bait after one bite while pearls remain.
        // Wait for the recovery animation plus a short rearm delay rather than
        // the previous 150-249 ticks that felt like a one-shot attack.
        long flowerDelay = kind == HuntType.PLAYER_PEARL
            ? charge + RECOVER_TICKS + 40 + level.getRandom().nextInt(21)
            : 150 + level.getRandom().nextInt(100);
        state.flowerCooldown.put(key, now + flowerDelay);
        state.targetCooldown.put(target.getUUID(), now + 95);
        notifyPose(level, pos, target, kind == HuntType.ENDERMITE
            ? MimicPosePacket.ENDERMITE_FEED : MimicPosePacket.PLAYER_BITE,
            MimicPosePacket.START);

        level.playSound(null, pos, SoundEvents.CHORUS_FLOWER_GROW, SoundSource.BLOCKS,
            0.7F, kind == HuntType.ENDERMITE ? 0.55F : 0.8F);
        level.sendParticles(ParticleTypes.PORTAL,
            pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
            kind == HuntType.ENDERMITE ? 6 : 10, .2, .25, .2, .025);
        return true;
    }

    /** Server tick, independent of the number of nearby players. */
    public static void tick(ServerLevel level) {
        State state = state(level);
        long now = level.getGameTime();
        Iterator<Map.Entry<Long, Strike>> it = state.charging.entrySet().iterator();
        MimicData data = MimicData.get(level);

        while (it.hasNext()) {
            Map.Entry<Long, Strike> entry = it.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            Strike strike = entry.getValue();
            if (!data.isInfected(pos) || !level.hasChunkAt(pos)
                || !level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
                it.remove();
                releaseMovingParts(level, pos);
                state.successfulHunts.remove(pos.asLong());
                notifyPose(level, pos, null, (byte) 0, MimicPosePacket.STOP);
                continue;
            }
            // Temporary in-world rig preview until the textured, client-side
            // animated renderer is ready. Never rendered while masquerading.
            // Each joint corresponds to one rigid future chorus stem segment;
            // no stretched UV. The two hunting types use different aim heights.
            if (now % 3L == 0L && now < strike.clearAt()) {
                Entity previewTarget = level.getEntity(strike.targetId());
                if (previewTarget != null && previewTarget.isAlive()) {
                    Vec3 aim = strike.kind() == HuntType.ENDERMITE
                        ? MimicRig.endermiteGroundAim(previewTarget.getX(),
                            previewTarget.getY(), previewTarget.getZ())
                        : new Vec3(previewTarget.getX(),
                            previewTarget.getY() + Math.min(1.55, previewTarget.getBbHeight() * .85),
                            previewTarget.getZ());
                    // Anchor to the vanilla stem below the flower. The whole
                    // plant is NOT replaced; the infected tip alone animates.
                    java.util.List<Vec3> branch = MimicBranchPath.trace(level, pos).stream()
                        .map(Vec3::atCenterOf).toList();
                    boolean recover = now >= strike.hitAt();
                    double phase = recover
                        ? (now - strike.hitAt()) / (double) RECOVER_TICKS
                        : (now - strike.startedAt()) / (double) (strike.hitAt() - strike.startedAt());
                    MimicRig.Pose pose = MimicRig.evaluateBranch(profile(level, pos), branch,
                        aim, phase, recover);
                    // Sparse ephemeral markers, NOT new permanent entities.
                    for (int joint = 1; joint < pose.joints().size(); joint++) {
                        Vec3 v = pose.joints().get(joint);
                        level.sendParticles(ParticleTypes.PORTAL, v.x, v.y, v.z,
                            1, .025, .025, .025, 0);
                    }
                    Vec3 mouth = pose.mouth();
                    if (pose.opening() > .45) {
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                            mouth.x, mouth.y, mouth.z, 4, .12, .12, .12, .015);
                    }
                }
            }
            if (now == strike.hitAt() - 8) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    12, .22, .2, .22, .015);
            }
            if (now == strike.hitAt()) {
                Entity target = level.getEntity(strike.targetId());
                if (target != null && target.isAlive()
                    && (strike.kind() == HuntType.ENDERMITE
                        ? withinMiteHuntRange(pos, target)
                        : target instanceof ServerPlayer player && mouthAtPlayer(level, pos, player))
                    && unobstructed(level, pos, target)) {
                    if (strike.kind() == HuntType.PLAYER_PEARL && target instanceof ServerPlayer player
                        && canHuntPlayer(player) && hasEnderPearl(player)) {
                        // Pearls are stolen at the hit tick, never when the
                        // mimic merely detects a carrier or starts charging.
                        // Damage and theft are one successful contact event.
                        // Invulnerable players must not lose items on a rejected hit.
                        if (player.hurtServer(level, level.damageSources().cactus(), 3.0F)) {
                            state.successfulHunts.add(pos.asLong());
                            int stolen = stealPlayerPearls(player);
                            level.playSound(null, pos, SoundEvents.SHULKER_BULLET_HIT,
                                SoundSource.HOSTILE, .8F, .85F);
                            level.sendParticles(ParticleTypes.CRIT, player.getX(),
                                player.getY() + 1.0, player.getZ(), 12, .2, .3, .2, .05);
                            if (stolen > 0) {
                                // A successful theft has its own brief effect,
                                // separate from the Endermite digestion timer.
                                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                                    stolen * 5, .24, .28, .24, .04);
                            }
                        }
                    } else if (strike.kind() == HuntType.ENDERMITE && target instanceof Endermite mite
                        && mouthAtPrey(level, pos, mite)) {
                        // The feeding animation must end at endermite height, not eye height.
                        level.sendParticles(ParticleTypes.PORTAL,
                            mite.getX(), mite.getY() + .12, mite.getZ(),
                            20, .2, .1, .2, .055);
                        level.playSound(null, pos, SoundEvents.SHULKER_BULLET_HIT,
                            SoundSource.HOSTILE, .55F, .55F);
                        mite.discard(); // prey eaten; no attack on a nearby player
                        state.successfulHunts.add(pos.asLong());
                        // Five seconds of Endermite portal motes emerge from
                        // the original infected blossom after a real swallow.
                        state.digestingUntil.put(pos.asLong(), now + DIGEST_TICKS);
                    }
                }
            }
            if (now >= strike.clearAt()) {
                it.remove();
                releaseMovingParts(level, pos);
                notifyPose(level, pos, null, (byte) 0, MimicPosePacket.STOP);
                // Successful prey capture can cause the *real vanilla flower*
                // to reattach elsewhere on its OWN stem once animation ends.
                if (state.successfulHunts.remove(pos.asLong()))
                    MimicRemodel.tryReposition(level, pos);
            } else if (now % 7L == 0) {
                Entity trackTarget = level.getEntity(strike.targetId());
                if (trackTarget != null && trackTarget.isAlive()) {
                    notifyPose(level, pos, trackTarget,
                        strike.kind() == HuntType.ENDERMITE ? MimicPosePacket.ENDERMITE_FEED
                            : MimicPosePacket.PLAYER_BITE,
                        MimicPosePacket.UPDATE);
                }
            }
        }

        // Independent of the active animation: continues after the stalk
        // has already returned to its resting vanilla shape.
        Iterator<Map.Entry<Long, Long>> digest = state.digestingUntil.entrySet().iterator();
        while (digest.hasNext()) {
            Map.Entry<Long, Long> entry = digest.next();
            BlockPos flower = BlockPos.of(entry.getKey());
            if (now >= entry.getValue() || !level.hasChunkAt(flower)
                || !data.isInfected(flower)
                || !level.getBlockState(flower).is(Blocks.CHORUS_FLOWER)) {
                digest.remove();
                continue;
            }
            if (now % 4L == 0L) {
                level.sendParticles(ParticleTypes.PORTAL,
                    flower.getX() + .5, flower.getY() + .46, flower.getZ() + .5,
                    3, .24, .25, .24, .018);
                if (now % 12L == 0L) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        flower.getX() + .5, flower.getY() + .52, flower.getZ() + .5,
                        1, .12, .15, .12, .005);
                }
            }
        }

        if (now % 2400L == 0) {
            state.flowerCooldown.entrySet().removeIf(e -> e.getValue() < now);
            state.targetCooldown.entrySet().removeIf(e -> e.getValue() < now);
        }
    }

    /**
     * Unattended feeding: an infected flower may eat a nearby Endermite even
     * when no player is standing next to it. Only loaded chunks are consulted.
     * A rolling cursor amortizes work across world ticks.
     */
    public static void seekUnattendedEndermites(ServerLevel level) {
        State state = state(level);
        MimicData data = MimicData.get(level);
        java.util.List<Long> flowers = data.infectedIds();
        if (flowers.isEmpty()) return;
        int budget = Math.min(128, flowers.size());
        int start = Math.floorMod(state.miteScanCursor, flowers.size());
        state.miteScanCursor = (start + budget) % flowers.size();
        long now = level.getGameTime();

        java.util.List<Candidate> options = new java.util.ArrayList<>();
        for (int i = 0; i < budget; i++) {
            BlockPos pos = BlockPos.of(flowers.get((start + i) % flowers.size()));
            if (!level.hasChunkAt(pos)) continue;
            long id = pos.asLong();
            if (state.charging.containsKey(id) || now < state.flowerCooldown.getOrDefault(id, 0L)) continue;
            if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
                data.reconcileFlower(level, pos);
                continue;
            }
            AABB area = AABB.ofSize(Vec3.atCenterOf(pos),
                MITE_HUNT_XZ * 2 + 2, MITE_HUNT_VERTICAL * 2 + 2, MITE_HUNT_XZ * 2 + 2);
            for (Endermite mite : level.getEntitiesOfClass(Endermite.class, area, Entity::isAlive)) {
                if (now < state.targetCooldown.getOrDefault(mite.getUUID(), 0L)) continue;
                if (!withinMiteHuntRange(pos, mite)) continue;
                if (!canReachMite(level, pos, mite)) continue;
                if (!unobstructed(level, pos, mite)) continue;
                options.add(new Candidate(pos, mite, HuntType.ENDERMITE));
            }
        }
        if (!options.isEmpty()) {
            Candidate choice = options.get(level.getRandom().nextInt(options.size()));
            begin(level, choice.flower(), choice.target(), choice.type(), false);
        }
    }

    /** The whole real attached branch may bend, but extra growth is capped at 1.5 blocks. */
    private static boolean canReachMite(ServerLevel level, BlockPos flower, Entity mite) {
        java.util.List<Vec3> resting = MimicBranchPath.trace(level, flower).stream()
            .map(Vec3::atCenterOf).toList();
        Vec3 aim = MimicRig.endermiteGroundAim(mite.getX(), mite.getY(), mite.getZ());
        return MimicRig.canReach(resting, aim);
    }

    /**
     * A player can be bitten only where the server-side articulated mouth
     * actually meets their hitbox. Checking distance from the resting flower
     * would both miss legitimate long-branch contacts and permit remote theft.
     * Keep the aim identical to notifyPose() and the animation preview.
     */
    private static boolean mouthAtPlayer(ServerLevel level, BlockPos flower, ServerPlayer player) {
        List<Vec3> resting = MimicBranchPath.trace(level, flower).stream()
            .map(Vec3::atCenterOf).toList();
        if (resting.size() < 2) return false;
        Vec3 aim = new Vec3(player.getX(),
            player.getY() + Math.min(1.55, player.getBbHeight() * .85), player.getZ());
        Vec3 mouth = MimicRig.evaluateBranch(
            profile(level, flower), resting, aim, 1.0, false).mouth();
        // Small contact allowance for a 14px-wide head, not a 4-block radius.
        return player.getBoundingBox().inflate(.35).contains(mouth);
    }

    /** Do not consume prey until the articulated flower has ACTUALLY reached it. */
    private static boolean mouthAtPrey(ServerLevel level, BlockPos flower, Endermite mite) {
        List<Vec3> resting = MimicBranchPath.trace(level, flower).stream()
            .map(Vec3::atCenterOf).toList();
        Vec3 mouthTarget = MimicRig.endermiteGroundAim(
            mite.getX(), mite.getY(), mite.getZ());
        MimicRig.Pose contact = MimicRig.evaluateBranch(
            profile(level, flower), resting, mouthTarget, 1.0, false);
        return contact.mouth().distanceToSqr(mouthTarget) <= .65 * .65;
    }

    private static void notifyPose(ServerLevel level, BlockPos flower, Entity target,
                                   byte kind, byte operation) {
        Vec3 aim = Vec3.atCenterOf(flower);
        if (target != null) {
            aim = target instanceof ServerPlayer
                ? new Vec3(target.getX(),
                    target.getY() + Math.min(1.55, target.getBbHeight() * .85),
                    target.getZ())
                : MimicRig.endermiteGroundAim(target.getX(), target.getY(), target.getZ());
        }
        // Critical: send the actual per-attack charge duration. The duration
        // grows for distant prey, and client + server must reach contact on
        // the same tick to avoid the disappearing-prey/empty-mouth bug.
        Strike current = state(level).charging.get(flower.asLong());
        int duration = current == null
            ? (kind == MimicPosePacket.ENDERMITE_FEED ? MITE_CHARGE_TICKS : PLAYER_CHARGE_TICKS)
            : (int)(current.hitAt() - current.startedAt());
        boolean dead = level.getBlockState(flower).is(Blocks.CHORUS_FLOWER)
            && level.getBlockState(flower).getValue(ChorusFlowerBlock.AGE) >= 5;
        MimicPosePacket packet = new MimicPosePacket(flower, aim,
            profile(level, flower).packed(), duration, RECOVER_TICKS, dead, kind, operation,
            MimicBranchPath.trace(level, flower));
        PacketDistributor.sendToPlayersNear(level, null,
            flower.getX() + .5, flower.getY() + .5, flower.getZ() + .5, 64, packet);
    }

    /**
     * Explicit diagnostic for the player looking at a marked flower.
     * Uses identical prey range and line-of-sight rules to automatic hunting.
     * The diagnosis is only exposed through the operator command.
     */
    public static String diagnose(ServerLevel level, BlockPos flower, ServerPlayer viewer) {
        if (!MimicData.get(level).isInfected(flower)) return "not infected";
        State state = state(level);
        long now = level.getGameTime();
        Strike strike = state.charging.get(flower.asLong());
        if (strike != null) {
            return "ACTIVE " + strike.kind().animation + ", ticks until contact="
                + Math.max(0L, strike.hitAt() - now);
        }

        long cooldown = Math.max(0L, state.flowerCooldown.getOrDefault(flower.asLong(), 0L) - now);
        AABB scan = AABB.ofSize(Vec3.atCenterOf(flower),
            MITE_HUNT_XZ * 2 + 2, MITE_HUNT_VERTICAL * 2 + 2, MITE_HUNT_XZ * 2 + 2);
        Endermite nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Endermite mite : level.getEntitiesOfClass(Endermite.class, scan, Entity::isAlive)) {
            double distance = Vec3.atCenterOf(flower).distanceToSqr(mite.position());
            if (distance < best) {
                best = distance;
                nearest = mite;
            }
        }
        if (nearest == null) return "SLEEPING, no alive Endermites within 18 horizontal / 24 vertical blocks; flower cooldown=" + cooldown + "t"
            + diagnosePearlBait(level, flower, viewer, cooldown);

        long targetCooldown = Math.max(0L,
            state.targetCooldown.getOrDefault(nearest.getUUID(), 0L) - now);
        return "SLEEPING, can reach physically=" + canReachMite(level, flower, nearest)
            + ", branch blocks=" + MimicBranchPath.trace(level,flower).size()
            + ", nearest Endermite distance=" + String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(best))
            + ", within wake range=" + withinMiteHuntRange(flower, nearest)
            + " (horizontal " + MITE_HUNT_XZ + ", vertical " + MITE_HUNT_VERTICAL + ")"
            + ", path clear=" + unobstructed(level, flower, nearest)
            + ", flower cooldown=" + cooldown + "t"
            + ", prey cooldown=" + targetCooldown + "t"
            + diagnosePearlBait(level, flower, viewer, cooldown);
    }

    /** Explain why an Ender Pearl carrier does or does not attract another bite. */
    private static String diagnosePearlBait(ServerLevel level, BlockPos flower,
                                           ServerPlayer viewer, long flowerCooldown) {
        int pearls = 0;
        for (int slot = 0; slot < viewer.getInventory().getContainerSize(); slot++) {
            var item = viewer.getInventory().getItem(slot);
            if (item.is(Items.ENDER_PEARL)) pearls += item.getCount();
        }
        long targetCooldown = Math.max(0L,
            state(level).targetCooldown.getOrDefault(viewer.getUUID(), 0L) - level.getGameTime());
        double distanceSq = Vec3.atCenterOf(flower).distanceToSqr(viewer.position());
        return " | player: pearls=" + pearls
            + ", eligible=" + canHuntPlayer(viewer)
            + ", in wake range=" + (distanceSq <= PLAYER_WAKE_SQR)
            + ", path clear=" + unobstructed(level, flower, viewer)
            + ", mouth reaches=" + mouthAtPlayer(level, flower, viewer)
            + ", flower cooldown=" + flowerCooldown + "t"
            + ", target cooldown=" + targetCooldown + "t";
    }

    /**
     * Ignore collisions with the infected flower and the local chorus
     * stalk supporting it. A vanilla flower's own stem must never prevent
     * its jaw from noticing prey directly under or beside it.
     * Other solid blocks (especially End stone) still block visibility.
     */
    private static boolean unobstructed(ServerLevel level, BlockPos pos, Entity target) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 end = target instanceof ServerPlayer player
            ? player.getEyePosition()
            : target.position().add(0, target.getBbHeight() * .5, 0);
        Vec3 difference = end.subtract(center);
        if (difference.lengthSqr() < 0.01) return true;
        Vec3 dir = difference.normalize();
        Vec3 start = center.add(dir.scale(0.15));

        java.util.List<BlockPos> owned = MimicBranchPath.trace(level, pos);
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockHitResult hit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.MISS) return true;
            BlockPos obstacle = hit.getBlockPos();
            if (!MimicBranchPath.owns(owned, obstacle) && !obstacle.equals(owned.get(0))) return false;

            Vec3 point = hit.getLocation();
            // Jump past the bounding block to avoid repeatedly striking
            // the same chorus collision shape. Do NOT jump through End stone.
            double exit = Double.POSITIVE_INFINITY;
            if (dir.x > 1e-6) exit = Math.min(exit, (obstacle.getX() + 1 - point.x) / dir.x);
            else if (dir.x < -1e-6) exit = Math.min(exit, (obstacle.getX() - point.x) / dir.x);
            if (dir.y > 1e-6) exit = Math.min(exit, (obstacle.getY() + 1 - point.y) / dir.y);
            else if (dir.y < -1e-6) exit = Math.min(exit, (obstacle.getY() - point.y) / dir.y);
            if (dir.z > 1e-6) exit = Math.min(exit, (obstacle.getZ() + 1 - point.z) / dir.z);
            else if (dir.z < -1e-6) exit = Math.min(exit, (obstacle.getZ() - point.z) / dir.z);

            if (!Double.isFinite(exit)) return false;
            Vec3 next = point.add(dir.scale(Math.max(0, exit) + 0.025));
            if (next.distanceToSqr(end) < 0.0001) return true;
            if (next.distanceToSqr(end) >= start.distanceToSqr(end) - 0.00001) return false;
            start = next;
        }
        return false;
    }

}
