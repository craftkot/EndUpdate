package com.ehtid.endupdate.world;

import com.ehtid.endupdate.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndTrialDungeonFeature extends Feature<NoneFeatureConfiguration> {
    public EndTrialDungeonFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        BlockPos candidate = ctx.origin();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate.getX(), candidate.getZ());
        BlockPos origin = new BlockPos(candidate.getX(), y, candidate.getZ());

        if (y < 35 || !hasStableFoundation(level, origin)) return false;

        buildCentralArena(level, origin);
        buildPerimeter(level, origin);
        buildPylons(level, origin);
        buildCoreDais(level, origin);

        // Four actual rooms with short corridors. Each has a different End-specific combat layout.
        buildCorridor(level, origin, 0, -1);
        buildChamber(level, origin.offset(0, 0, -18), 0, 0, -1, random);

        buildCorridor(level, origin, 0, 1);
        buildChamber(level, origin.offset(0, 0, 18), 1, 0, 1, random);

        buildCorridor(level, origin, -1, 0);
        buildChamber(level, origin.offset(-18, 0, 0), 2, -1, 0, random);

        buildCorridor(level, origin, 1, 0);
        buildChamber(level, origin.offset(18, 0, 0), 3, 1, 0, random);

        return true;
    }

    private boolean hasStableFoundation(WorldGenLevel level, BlockPos origin) {
        int[][] samples = {
                {0, 0}, {18, 0}, {-18, 0}, {0, 18}, {0, -18},
                {12, 12}, {-12, 12}, {12, -12}, {-12, -12}
        };
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int[] sample : samples) {
            int x = origin.getX() + sample[0];
            int z = origin.getZ() + sample[1];
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos top = new BlockPos(x, h - 1, z);
            if (!level.getBlockState(top).is(Blocks.END_STONE)) return false;
            minY = Math.min(minY, h);
            maxY = Math.max(maxY, h);
        }
        return maxY - minY <= 4;
    }

    private void buildCentralArena(WorldGenLevel level, BlockPos origin) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (Math.abs(x) + Math.abs(z) > 20) continue;
                BlockPos floor = origin.offset(x, 0, z);
                BlockState floorState = ((x + z) & 3) == 0
                        ? Blocks.PURPUR_BLOCK.defaultBlockState()
                        : Blocks.END_STONE_BRICKS.defaultBlockState();
                set(level, floor, floorState);
                supportFloor(level, floor);
                clearColumn(level, floor, 1, 6);
            }
        }
    }

    private void buildPerimeter(WorldGenLevel level, BlockPos origin) {
        for (int i = -10; i <= 10; i++) {
            buildWallSegment(level, origin.offset(i, 1, -10), i);
            buildWallSegment(level, origin.offset(i, 1, 10), i);
            buildWallSegment(level, origin.offset(-10, 1, i), i);
            buildWallSegment(level, origin.offset(10, 1, i), i);
        }
    }

    private void buildPylons(WorldGenLevel level, BlockPos origin) {
        int[][] pylons = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
        for (int[] p : pylons) {
            for (int h = 1; h <= 6; h++) {
                set(level, origin.offset(p[0], h, p[1]), Blocks.OBSIDIAN.defaultBlockState());
            }
            set(level, origin.offset(p[0], 7, p[1]), Blocks.END_ROD.defaultBlockState());
        }
    }

    private void buildCoreDais(WorldGenLevel level, BlockPos origin) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockState state = (Math.abs(x) == 2 || Math.abs(z) == 2)
                        ? ModBlocks.CHORUS_MOSAIC.get().defaultBlockState()
                        : Blocks.PURPUR_PILLAR.defaultBlockState();
                set(level, origin.offset(x, 1, z), state);
            }
        }
        set(level, origin.offset(0, 2, 0), ModBlocks.END_TRIAL_CORE.get().defaultBlockState());
    }

    private void buildWallSegment(WorldGenLevel level, BlockPos base, int along) {
        if (Math.abs(along) <= 2) return; // four wide entrances lead to the side rooms
        for (int h = 0; h < 4; h++) {
            BlockState state = h == 3 && (along & 1) == 0
                    ? Blocks.PURPUR_PILLAR.defaultBlockState()
                    : Blocks.END_STONE_BRICKS.defaultBlockState();
            set(level, base.above(h), state);
        }
    }

    private void buildCorridor(WorldGenLevel level, BlockPos origin, int dx, int dz) {
        for (int distance = 10; distance <= 13; distance++) {
            for (int side = -2; side <= 2; side++) {
                int x = dx * distance + (dz != 0 ? side : 0);
                int z = dz * distance + (dx != 0 ? side : 0);
                BlockPos floor = origin.offset(x, 0, z);
                set(level, floor, (side == 0 ? ModBlocks.CHORUS_PLANKS.get() : Blocks.END_STONE_BRICKS).defaultBlockState());
                supportFloor(level, floor);
                clearColumn(level, floor, 1, 5);
            }
        }
    }

    private void buildChamber(WorldGenLevel level, BlockPos center, int style, int outwardX, int outwardZ, RandomSource random) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                BlockPos floor = center.offset(x, 0, z);
                BlockState floorState = ((x * x + z * z + style) % 5 == 0)
                        ? ModBlocks.CHORUS_PLANKS.get().defaultBlockState()
                        : Blocks.END_STONE_BRICKS.defaultBlockState();
                set(level, floor, floorState);
                supportFloor(level, floor);
                clearColumn(level, floor, 1, 6);

                boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
                if (!edge || isEntrance(x, z, outwardX, outwardZ)) continue;
                for (int h = 1; h <= 4; h++) {
                    BlockState wall = h == 4 && ((x + z) & 1) == 0
                            ? Blocks.PURPUR_BLOCK.defaultBlockState()
                            : Blocks.END_STONE_BRICKS.defaultBlockState();
                    set(level, floor.above(h), wall);
                }
            }
        }

        // Roof rim keeps the room readable while leaving the centre open to the End sky.
        for (int i = -5; i <= 5; i++) {
            set(level, center.offset(i, 5, -5), Blocks.PURPUR_SLAB.defaultBlockState());
            set(level, center.offset(i, 5, 5), Blocks.PURPUR_SLAB.defaultBlockState());
            set(level, center.offset(-5, 5, i), Blocks.PURPUR_SLAB.defaultBlockState());
            set(level, center.offset(5, 5, i), Blocks.PURPUR_SLAB.defaultBlockState());
        }

        switch (style) {
            case 0 -> buildChorusPillarRoom(level, center);
            case 1 -> buildObsidianBlinkRoom(level, center);
            case 2 -> buildPurpurStepsRoom(level, center);
            default -> buildCrossfireRoom(level, center, random);
        }
    }

    private boolean isEntrance(int x, int z, int outwardX, int outwardZ) {
        // The doorway is on the chamber wall facing back toward the central arena.
        if (outwardX == 1 && x == -5) return Math.abs(z) <= 1;
        if (outwardX == -1 && x == 5) return Math.abs(z) <= 1;
        if (outwardZ == 1 && z == -5) return Math.abs(x) <= 1;
        if (outwardZ == -1 && z == 5) return Math.abs(x) <= 1;
        return false;
    }

    private void buildChorusPillarRoom(WorldGenLevel level, BlockPos center) {
        int[][] points = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : points) {
            for (int h = 1; h <= 3; h++) {
                set(level, center.offset(p[0], h, p[1]), ModBlocks.CHORUS_STEM.get().defaultBlockState());
            }
            set(level, center.offset(p[0], 4, p[1]), Blocks.END_ROD.defaultBlockState());
        }
        for (int i = -2; i <= 2; i++) {
            set(level, center.offset(i, 1, 0), ModBlocks.CHORUS_MOSAIC.get().defaultBlockState());
        }
    }

    private void buildObsidianBlinkRoom(WorldGenLevel level, BlockPos center) {
        int[][] points = {{0, -3}, {0, 3}, {-3, 0}, {3, 0}};
        for (int[] p : points) {
            for (int h = 1; h <= 4; h++) {
                set(level, center.offset(p[0], h, p[1]), Blocks.OBSIDIAN.defaultBlockState());
            }
            set(level, center.offset(p[0], 5, p[1]), Blocks.END_ROD.defaultBlockState());
        }
    }

    private void buildPurpurStepsRoom(WorldGenLevel level, BlockPos center) {
        for (int ring = 0; ring <= 2; ring++) {
            int y = ring + 1;
            int radius = 3 - ring;
            for (int x = -radius; x <= radius; x++) {
                set(level, center.offset(x, y, -radius), Blocks.PURPUR_BLOCK.defaultBlockState());
                set(level, center.offset(x, y, radius), Blocks.PURPUR_BLOCK.defaultBlockState());
            }
            for (int z = -radius + 1; z < radius; z++) {
                set(level, center.offset(-radius, y, z), Blocks.PURPUR_BLOCK.defaultBlockState());
                set(level, center.offset(radius, y, z), Blocks.PURPUR_BLOCK.defaultBlockState());
            }
        }
    }

    private void buildCrossfireRoom(WorldGenLevel level, BlockPos center, RandomSource random) {
        for (int i = -4; i <= 4; i += 2) {
            int offset = random.nextBoolean() ? 2 : -2;
            set(level, center.offset(i, 1, offset), ModBlocks.CHORUS_MOSAIC.get().defaultBlockState());
            set(level, center.offset(offset, 1, i), Blocks.PURPUR_PILLAR.defaultBlockState());
        }
        set(level, center.above(), Blocks.END_ROD.defaultBlockState());
    }

    private void supportFloor(WorldGenLevel level, BlockPos floor) {
        for (int depth = 1; depth <= 5; depth++) {
            BlockPos below = floor.below(depth);
            if (level.getBlockState(below).isAir()) {
                set(level, below, Blocks.END_STONE_BRICKS.defaultBlockState());
            }
        }
    }

    private void clearColumn(WorldGenLevel level, BlockPos floor, int minY, int maxY) {
        for (int h = minY; h <= maxY; h++) {
            BlockPos pos = floor.above(h);
            if (!level.getBlockState(pos).isAir()) {
                set(level, pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
    }
}
