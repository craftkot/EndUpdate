package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Actual vanilla block topology, root -> infected flower. The flower is the
 * infection anchor; supporting chorus_plant blocks provide the appendage.
 * Stop at junctions: a shared trunk cannot be bent without also bending
 * healthy flowers. No new block IDs, saved fake branch shapes or forced chunks.
 */
public final class MimicBranchPath {
    public static final int MAX_BLOCKS = 24;

    private MimicBranchPath() {}

    public static List<BlockPos> trace(ServerLevel level, BlockPos flower) {
        if (!level.hasChunkAt(flower) || !level.getBlockState(flower).is(Blocks.CHORUS_FLOWER))
            return List.of();

        ArrayList<BlockPos> reversed = new ArrayList<>();
        reversed.add(flower.immutable());
        BlockPos parent = pickParent(level, flower, null);
        BlockPos child = flower;

        while (parent != null && reversed.size() < MAX_BLOCKS) {
            reversed.add(parent.immutable());
            if (degree(level, parent) != 2) break; // first branch junction OR base
            BlockPos next = pickParent(level, parent, child);
            if (next == null || reversed.contains(next)) break;
            child = parent;
            parent = next;
        }
        if (reversed.size() == 1) {
            // No attached stalk: still give the flower a fixed pivot, but
            // never mask or mutate this imaginary root block.
            reversed.add(flower.below());
        }
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static int degree(ServerLevel level, BlockPos at) {
        int n = 0;
        for (Direction direction : Direction.values()) {
            BlockPos other = at.relative(direction);
            if (!level.hasChunkAt(other)) continue;
            if (isChorus(level, other)) n++;
        }
        return n;
    }

    private static BlockPos pickParent(ServerLevel level, BlockPos at, BlockPos from) {
        // Prefer the downward stem but handle real horizontal branches as well.
        Direction[] directions = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.EAST, Direction.WEST, Direction.UP
        };
        for (Direction direction : directions) {
            BlockPos next = at.relative(direction);
            if ((from == null || !next.equals(from))
                && level.hasChunkAt(next)
                && level.getBlockState(next).is(Blocks.CHORUS_PLANT)) return next;
        }
        return null;
    }

    private static boolean isChorus(ServerLevel level, BlockPos at) {
        return level.getBlockState(at).is(Blocks.CHORUS_PLANT)
            || level.getBlockState(at).is(Blocks.CHORUS_FLOWER);
    }

    /** The flower owns every path element except its shared/root pivot. */
    public static boolean owns(List<BlockPos> path, BlockPos at) {
        for (int i = 1; i < path.size(); i++) {
            if (path.get(i).equals(at)) return true;
        }
        return false;
    }
}
