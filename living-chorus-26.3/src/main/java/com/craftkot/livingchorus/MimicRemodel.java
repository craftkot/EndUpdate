package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * Safely restructure a mature infected chorus using ordinary vanilla blocks.
 * Move ONLY the flower tip, never a common trunk or any neighboring branch.
 * This is done after an attack has recovered and the original collision
 * and visual mask have been released.
 */
public final class MimicRemodel {
    private MimicRemodel() {}

    public static boolean tryReposition(ServerLevel level, BlockPos flower) {
        MimicData infection = MimicData.get(level);
        if (!infection.isInfected(flower) || !level.hasChunkAt(flower)) return false;
        BlockState original = level.getBlockState(flower);
        if (!original.is(Blocks.CHORUS_FLOWER)) return false;
        List<BlockPos> path = MimicBranchPath.trace(level, flower);
        if (path.size() < 3) return false; // root / first fork is never movable
        MimicProfile profile = MimicAwakening.profile(level, flower);

        // Use only stem blocks truly owned by this flower, never the
        // first/shared root. Prefer moving to an earlier height/branch.
        List<BlockPos> choices = new ArrayList<>();
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST, Direction.UP};
        for (int i = 1; i < path.size() - 1; i++) {
            BlockPos attachment = path.get(i);
            if (!level.getBlockState(attachment).is(Blocks.CHORUS_PLANT)) continue;
            for (int j = 0; j < sides.length; j++) {
                Direction direction = sides[(j + profile.direction()) % sides.length];
                BlockPos candidate = attachment.relative(direction);
                if (candidate.equals(flower) || !level.hasChunkAt(candidate)) continue;
                // Keep changes localized, so a single bite cannot restructure
                // a huge region or collide with a healthy chorus tip.
                int distance = Math.abs(candidate.getX() - flower.getX())
                    + Math.abs(candidate.getY() - flower.getY())
                    + Math.abs(candidate.getZ() - flower.getZ());
                if (distance > 4 || distance < 1) continue;
                if (!level.getBlockState(candidate).isAir()
                    || !original.canSurvive(level, candidate)) continue;
                choices.add(candidate.immutable());
            }
        }
        if (choices.isEmpty()) return false;

        // Deterministic ordering plus per-bite time produces real variations
        // without world-scanning or storing a new block/entity type.
        int index = Math.floorMod((int) (level.getGameTime() / 17)
            + profile.firstBend() * 3 + profile.secondBend(), choices.size());
        BlockPos newTip = choices.get(index);
        if (!level.setBlock(newTip, original, 3)) return false;
        if (!level.setBlock(flower, Blocks.CHORUS_PLANT.defaultBlockState(), 3)) {
            level.setBlock(newTip, Blocks.AIR.defaultBlockState(), 3);
            return false;
        }
        if (!infection.transferFlower(flower, newTip)) {
            level.setBlock(flower, original, 3);
            level.setBlock(newTip, Blocks.AIR.defaultBlockState(), 3);
            return false;
        }
        level.sendParticles(ParticleTypes.PORTAL,
            newTip.getX() + .5, newTip.getY() + .5, newTip.getZ() + .5,
            14, .25, .25, .25, .025);
        return true;
    }
}
