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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private static final double MITE_WAKE_SQR = 5.2 * 5.2;
    private static final double PLAYER_HIT_SQR = 4.0 * 4.0;
    private static final double MITE_HIT_SQR = 4.5 * 4.5;
    private static final int PLAYER_CHARGE_TICKS = 35;
    private static final int MITE_CHARGE_TICKS = 46;
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

    private record Strike(UUID targetId, HuntType kind, long hitAt, long clearAt) { }
    private record Candidate(BlockPos flower, Entity target, HuntType type) { }

    private static final class State {
        final Map<Long, Strike> charging = new HashMap<>();
        final Map<Long, Long> flowerCooldown = new HashMap<>();
        final Map<UUID, Long> targetCooldown = new HashMap<>();
    }

    private MimicAwakening() { }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new State());
    }

    public static int activeCount(ServerLevel level) {
        return state(level).charging.size();
    }

    public static boolean isSleeping(ServerLevel level, BlockPos flower) {
        // Cooldown is not an animation: dormant flowers are vulnerable between attacks.
        return !state(level).charging.containsKey(flower.asLong());
    }

    public static void onFlowerDestroyed(ServerLevel level, BlockPos flower) {
        State state = state(level);
        long id = flower.asLong();
        state.charging.remove(id);
        state.flowerCooldown.remove(id);
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
                        data.forget(cursor);
                        continue;
                    }
                    BlockPos flower = cursor.immutable();
                    Vec3 source = Vec3.atCenterOf(flower);
                    for (Endermite mite : mites) {
                        if (now < state.targetCooldown.getOrDefault(mite.getUUID(), 0L)) continue;
                        if (source.distanceToSqr(mite.position()) <= MITE_WAKE_SQR
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
        AABB area = AABB.ofSize(Vec3.atCenterOf(pos), 11, 12, 11);
        for (Endermite mite : level.getEntitiesOfClass(Endermite.class, area, Entity::isAlive)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(mite.position()) <= MITE_WAKE_SQR) {
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

    private static boolean begin(ServerLevel level, BlockPos pos, Entity target,
                                 HuntType kind, boolean force) {
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            MimicData.get(level).forget(pos);
            return false;
        }
        State state = state(level);
        long key = pos.asLong();
        long now = level.getGameTime();
        if (state.charging.containsKey(key)) return false;
        if (!force && (now < state.flowerCooldown.getOrDefault(key, 0L)
            || now < state.targetCooldown.getOrDefault(target.getUUID(), 0L))) return false;

        int charge = kind == HuntType.ENDERMITE ? MITE_CHARGE_TICKS : PLAYER_CHARGE_TICKS;
        state.charging.put(key, new Strike(target.getUUID(), kind, now + charge,
            now + charge + RECOVER_TICKS));
        state.flowerCooldown.put(key, now + 150 + level.getRandom().nextInt(100));
        state.targetCooldown.put(target.getUUID(), now + 95);

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
                continue;
            }
            if (now == strike.hitAt() - 8) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    12, .22, .2, .22, .015);
            }
            if (now == strike.hitAt()) {
                Entity target = level.getEntity(strike.targetId());
                if (target != null && target.isAlive()
                    && Vec3.atCenterOf(pos).distanceToSqr(target.position()) <=
                        (strike.kind() == HuntType.ENDERMITE ? MITE_HIT_SQR : PLAYER_HIT_SQR)
                    && unobstructed(level, pos, target)) {
                    if (strike.kind() == HuntType.PLAYER_PEARL && target instanceof ServerPlayer player
                        && canHuntPlayer(player) && hasEnderPearl(player)) {
                        player.hurtServer(level, level.damageSources().cactus(), 3.0F);
                        level.playSound(null, pos, SoundEvents.SHULKER_BULLET_HIT,
                            SoundSource.HOSTILE, .8F, .85F);
                        level.sendParticles(ParticleTypes.CRIT, player.getX(),
                            player.getY() + 1.0, player.getZ(), 12, .2, .3, .2, .05);
                    } else if (strike.kind() == HuntType.ENDERMITE && target instanceof Endermite mite) {
                        // The feeding animation must end at endermite height, not eye height.
                        level.sendParticles(ParticleTypes.PORTAL,
                            mite.getX(), mite.getY() + .12, mite.getZ(),
                            20, .2, .1, .2, .055);
                        level.playSound(null, pos, SoundEvents.SHULKER_BULLET_HIT,
                            SoundSource.HOSTILE, .55F, .55F);
                        mite.discard(); // prey eaten; no attack on a nearby player
                    }
                }
            }
            if (now >= strike.clearAt()) it.remove();
        }

        if (now % 2400L == 0) {
            state.flowerCooldown.entrySet().removeIf(e -> e.getValue() < now);
            state.targetCooldown.entrySet().removeIf(e -> e.getValue() < now);
        }
    }

    private static boolean unobstructed(ServerLevel level, BlockPos pos, Entity target) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 end = target instanceof ServerPlayer player
            ? player.getEyePosition()
            : target.position().add(0, target.getBbHeight() * .5, 0);
        Vec3 delta = end.subtract(center);
        if (delta.lengthSqr() < .01) return true;
        Vec3 start = center.add(delta.normalize().scale(.8));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
