package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rare supplemental Endermite population in The End, around chorus forests.
 * No new entity IDs, no changes to vanilla dimensions outside The End.
 *
 * Uses a small periodic spawn budget instead of forcing chunk generation.
 * Endermites can despawn under their existing vanilla lifetime rules.
 */
public final class EndEndermiteSpawning {
    private static final int ATTEMPT_INTERVAL = 600; // 30 seconds, per player
    private static final int ATTEMPTS_PER_CHECK = 6;
    private static final int LOCAL_CAP = 6; // within 56 blocks of one player
    private static final int LOCAL_RADIUS = 56;

    private EndEndermiteSpawning() { }

    public static void tickPlayer(ServerLevel level, ServerPlayer player) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || player.isSpectator()) return;
        if (player.tickCount % ATTEMPT_INTERVAL != 0) return;
        // Rare by design; even with several players the same local cap applies.
        if (level.getRandom().nextInt(3) != 0) return;

        AABB populationArea = AABB.ofSize(player.position(),
            LOCAL_RADIUS * 2, 72, LOCAL_RADIUS * 2);
        int nearby = level.getEntitiesOfClass(Endermite.class, populationArea,
            Endermite::isAlive).size();
        if (nearby >= LOCAL_CAP) return;

        for (int attempt = 0; attempt < ATTEMPTS_PER_CHECK; attempt++) {
            int dx = 18 + level.getRandom().nextInt(19);
            int dz = 18 + level.getRandom().nextInt(19);
            if (level.getRandom().nextBoolean()) dx = -dx;
            if (level.getRandom().nextBoolean()) dz = -dz;

            int x = player.getBlockX() + dx;
            int z = player.getBlockZ() + dz;
            // Do not ever generate a new chunk solely for this feature.
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY() || y >= level.getMaxY() - 2) continue;
            BlockPos spot = new BlockPos(x, y, z);

            if (!level.getBlockState(spot.below()).is(Blocks.END_STONE)
                || !level.getBlockState(spot).isAir()
                || !level.getBlockState(spot.above()).isAir()) continue;
            // Favour chorus forests rather than artificially spawning all over The End.
            if (!nearChorus(level, spot)) continue;

            Endermite mite = EntityType.ENDERMITE.create(level, EntitySpawnReason.NATURAL);
            if (mite == null) return;
            mite.moveTo(x + .5, y, z + .5, level.getRandom().nextFloat() * 360F, 0);
            if (!level.noCollision(mite)) continue;

            level.addFreshEntity(mite);
            break; // only one Endermite per successful interval
        }
    }

    private static boolean nearChorus(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx += 2) {
            for (int dz = -5; dz <= 5; dz += 2) {
                for (int dy = 0; dy <= 8; dy += 2) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) continue;
                    if (level.getBlockState(cursor).is(Blocks.CHORUS_PLANT)
                        || level.getBlockState(cursor).is(Blocks.CHORUS_FLOWER)) return true;
                }
            }
        }
        return false;
    }
}
