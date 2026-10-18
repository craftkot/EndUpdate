package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Step 2: wake and strike using only vanilla chorus_flower blocks.
 *
 * This is deliberately server-only. No entity is created, no block is replaced
 * and there is no changed collision or item outline in the disguised state.
 * The animated V8 Blockbench rig will be integrated separately in step 3.
 */
public final class MimicAwakening {
    private static final int DETECT_XZ = 4;
    private static final int DETECT_Y = 3;
    private static final double TRIGGER_RANGE_SQR = 4.0 * 4.0;
    private static final double HIT_RANGE_SQR = 2.8 * 2.8;
    private static final int CHARGE_TICKS = 30; // 1.5 seconds at 20 TPS
    private static final int RECOVER_TICKS = 16;
    private static final Map<ServerLevel, State> STATES = new WeakHashMap<>();

    private record Strike(UUID playerId, long hitAt, long clearAt) { }

    private static final class State {
        final Map<Long, Strike> charging = new HashMap<>();
        final Map<Long, Long> flowerCooldown = new HashMap<>();
        final Map<UUID, Long> playerCooldown = new HashMap<>();
    }

    private MimicAwakening() { }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new State());
    }

    public static int activeCount(ServerLevel level) {
        return state(level).charging.size();
    }

    /** Called at most twice a second per player. No chunks are loaded. */
    public static void seek(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator() || player.isCreative() || !player.isAlive()) return;
        State state = state(level);
        long now = level.getGameTime();
        if (now < state.playerCooldown.getOrDefault(player.getUUID(), 0L)) return;

        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        MimicData data = MimicData.get(level);

        // Reservoir selection distributes attacks among all reachable infected
        // flower tips instead of always preferring one direction or branch.
        BlockPos chosen = null;
        int eligible = 0;
        for (int x = center.getX() - DETECT_XZ; x <= center.getX() + DETECT_XZ; x++) {
            for (int z = center.getZ() - DETECT_XZ; z <= center.getZ() + DETECT_XZ; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = center.getY() - DETECT_Y; y <= center.getY() + DETECT_Y; y++) {
                    cursor.set(x, y, z);
                    if (!data.isInfected(cursor)) continue;
                    if (now < state.flowerCooldown.getOrDefault(cursor.asLong(), 0L)) continue;
                    if (state.charging.containsKey(cursor.asLong())) continue;
                    if (player.distanceToSqr(Vec3.atCenterOf(cursor)) > TRIGGER_RANGE_SQR) continue;
                    if (!level.getBlockState(cursor).is(Blocks.CHORUS_FLOWER)) {
                        data.forget(cursor);
                        continue;
                    }
                    if (!unobstructed(level, cursor, player)) continue;
                    eligible++;
                    if (level.getRandom().nextInt(eligible) == 0) chosen = cursor.immutable();
                }
            }
        }
        if (chosen != null) begin(level, chosen, player, false);
    }

    /** Operator-only diagnostic: bypass cooldown, but never attack a normal flower. */
    public static boolean provoke(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!MimicData.get(level).isInfected(pos)) return false;
        return begin(level, pos, player, true);
    }

    private static boolean begin(ServerLevel level, BlockPos pos, ServerPlayer player, boolean force) {
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            MimicData.get(level).forget(pos);
            return false;
        }
        State state = state(level);
        long key = pos.asLong();
        long now = level.getGameTime();
        if (state.charging.containsKey(key)) return false;
        if (!force && (now < state.flowerCooldown.getOrDefault(key, 0L)
            || now < state.playerCooldown.getOrDefault(player.getUUID(), 0L))) return false;

        state.charging.put(key, new Strike(player.getUUID(), now + CHARGE_TICKS,
            now + CHARGE_TICKS + RECOVER_TICKS));
        state.flowerCooldown.put(key, now + 130 + level.getRandom().nextInt(100)); // 6.5–11.5 s
        state.playerCooldown.put(player.getUUID(), now + 85); // no simultaneous dogpile
        // Wake-up telegraph. Before this moment the flower is completely vanilla.
        level.playSound(null, pos, SoundEvents.CHORUS_FLOWER_GROW, SoundSource.BLOCKS, 0.8F, 0.75F);
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + .5, pos.getY() + .6,
            pos.getZ() + .5, 10, .20, .25, .20, .02);
        return true;
    }

    /** Runs exactly once per End server tick, independent of number of players. */
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
                it.remove(); // destroyed/replaced while preparing to attack
                continue;
            }

            if (now == strike.hitAt() - 8) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + .5,
                    pos.getY() + .5, pos.getZ() + .5, 16, .18, .20, .18, .015);
            }
            if (now == strike.hitAt()) {
                ServerPlayer target = (ServerPlayer) level.getPlayerByUUID(strike.playerId());
                if (target != null && target.isAlive() && !target.isCreative() && !target.isSpectator()
                    && target.distanceToSqr(Vec3.atCenterOf(pos)) <= HIT_RANGE_SQR
                    && unobstructed(level, pos, target)) {
                    target.hurtServer(level, level.damageSources().cactus(), 3.0F);
                    level.playSound(null, pos, SoundEvents.SHULKER_BULLET_HIT,
                        SoundSource.HOSTILE, .85F, .8F);
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1,
                        target.getZ(), 12, .25, .4, .25, .05);
                }
            }
            if (now >= strike.clearAt()) it.remove();
        }
        // Prevent accumulation over long play sessions.
        if (now % 2400L == 0) {
            state.flowerCooldown.entrySet().removeIf(e -> e.getValue() < now);
            state.playerCooldown.entrySet().removeIf(e -> e.getValue() < now);
        }
    }

    /** Collidable intervening blocks stop a bite; the source flower is ignored. */
    private static boolean unobstructed(ServerLevel level, BlockPos pos, ServerPlayer target) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 end = target.getEyePosition();
        Vec3 delta = end.subtract(center);
        if (delta.lengthSqr() < 0.01) return true;
        Vec3 start = center.add(delta.normalize().scale(0.80));
        BlockHitResult hit = level.clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
