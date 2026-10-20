package com.craftkot.chorusplague;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bounded cross-tree transmission following a REAL successful hunt.
 * Never changes vanilla blocks, loads chunks, or infects sibling tips.
 */
public final class PlagueSpores {
    private static final int RANGE_HORIZONTAL = 10;
    private static final int RANGE_VERTICAL = 6;
    private static final int LOCAL_CAP = 8;
    private static final long COOLDOWN = 20L * 50L;
    private static final Map<ServerLevel, Map<Long, Long>> NEXT_PULSE = new WeakHashMap<>();

    private PlagueSpores() {}

    public static boolean pulse(ServerLevel level, BlockPos source, boolean forced) {
        if (level.dimension() != Level.END || !level.hasChunkAt(source)) return false;
        MimicData infection = MimicData.get(level);
        if (!infection.isInfected(source)
            || !level.getBlockState(source).is(Blocks.CHORUS_FLOWER)) return false;
        if (infection.infectionsNear(source, 16) >= LOCAL_CAP) return false;
        long now = level.getGameTime();
        Map<Long, Long> cooldowns = NEXT_PULSE.computeIfAbsent(level, unused -> new HashMap<>());
        long key = source.asLong();
        if (!forced && now < cooldowns.getOrDefault(key, 0L)) return false;
        if (!forced && level.getRandom().nextInt(3) != 0) return false;
        cooldowns.put(key, now + COOLDOWN);
        if (cooldowns.size() > 4096) cooldowns.entrySet().removeIf(e -> e.getValue() < now);

        ArrayList<BlockPos> candidates = new ArrayList<>();
        for (int dx = -RANGE_HORIZONTAL; dx <= RANGE_HORIZONTAL; dx++) {
            for (int dz = -RANGE_HORIZONTAL; dz <= RANGE_HORIZONTAL; dz++) {
                if (dx * dx + dz * dz > RANGE_HORIZONTAL * RANGE_HORIZONTAL) continue;
                int x = source.getX() + dx, z = source.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int dy = -RANGE_VERTICAL; dy <= RANGE_VERTICAL; dy++) {
                    int y = source.getY() + dy;
                    if (y < level.getMinY() || y >= level.getMaxY()) continue;
                    BlockPos target = new BlockPos(x, y, z);
                    if (!target.equals(source)
                        && level.getBlockState(target).is(Blocks.CHORUS_FLOWER)
                        && !infection.isInfected(target)) candidates.add(target);
                }
            }
        }

        int attempts = Math.min(12, candidates.size());
        for (int i = 0; i < attempts; i++) {
            BlockPos target = candidates.remove(level.getRandom().nextInt(candidates.size()));
            if (!infection.infectFromSpore(level, target)) continue;
            level.sendParticles(ParticleTypes.PORTAL,
                source.getX() + .5, source.getY() + .5, source.getZ() + .5,
                24, .3, .4, .3, .07);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                target.getX() + .5, target.getY() + .5, target.getZ() + .5,
                36, .4, .5, .4, .08);
            level.playSound(null, target, SoundEvents.CHORUS_FLOWER_GROW,
                SoundSource.BLOCKS, .9F, .6F);
            return true;
        }
        return false;
    }
}
