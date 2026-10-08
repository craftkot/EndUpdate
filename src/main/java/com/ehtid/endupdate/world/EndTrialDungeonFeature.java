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
        buildGateArches(level, origin);
        buildPylons(level, origin);
        buildCoreDais(level, origin);
        buildCornerButtresses(level, origin);

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

                int ring = Math.max(Math.abs(x), Math.abs(z));
                BlockState floorState;
                if (ring == 11 || ring == 7) {
                    floorState = Blocks.PURPUR_BLOCK.defaultBlockState();
                } else if ((x == 0 || z == 0) && ring > 3) {
                    floorState = ModBlocks.CHORUS_PLANKS.get().defaultBlockState();
                } else if (((x + z) & 5) == 0) {
                    floorState = ModBlocks.CHORUS_MOSAIC.get().defaultBlockState();
                } else {
                    floorState = Blocks.END_STONE_BRICKS.defaultBlockState();
                }

                set(level, floor, floorState);
                supportFloor(level, floor);
                clearColumn(level, floor, 1, 7);
            }
        }

        // Small recessed-looking ring around the central pedestal.
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) == 4) {
                    set(level, origin.offset(x, 1, z), Blocks.PURPUR_SLAB.defaultBlockState());
                }
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

    private void buildGateArches(WorldGenLevel level, BlockPos origin) {
        buildGateArch(level, origin, 0, -10, true);
        buildGateArch(level, origin, 0, 10, true);
        buildGateArch(level, origin, -10, 0, false);
        buildGateArch(level, origin, 10, 0, false);
    }

    private void buildGateArch(WorldGenLevel level, BlockPos origin, int x, int z, boolean eastWestSpan) {
        for (int side = -3; side <= 3; side++) {
            int px = x + (eastWestSpan ? side : 0);
            int pz = z + (eastWestSpan ? 0 : side);
            if (Math.abs(side) >= 2) {
                for (int h = 1; h <= 6; h++) {
                    set(level, origin.offset(px, h, pz),
                            h <= 4 ? Blocks.END_STONE_BRICKS.defaultBlockState() : Blocks.PURPUR_PILLAR.defaultBlockState());
                }
            } else {
                set(level, origin.offset(px, 6, pz), Blocks.PURPUR_BLOCK.defaultBlockState());
            }
        }
        set(level, origin.offset(x, 7, z), Blocks.END_ROD.defaultBlockState());
    }

    private void buildPylons(WorldGenLevel level, BlockPos origin) {
        int[][] pylons = {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
        for (int[] p : pylons) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                    set(level, origin.offset(p[0] + dx, 1, p[1] + dz), Blocks.PURPUR_BLOCK.defaultBlockState());
                }
            }
            for (int h = 2; h <= 7; h++) {
                set(level, origin.offset(p[0], h, p[1]),
                        (h == 4 || h == 7) ? Blocks.PURPUR_PILLAR.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
            }
            set(level, origin.offset(p[0], 8, p[1]), Blocks.END_ROD.defaultBlockState());
        }
    }

    private void buildCornerButtresses(WorldGenLevel level, BlockPos origin) {
        int[][] corners = {{-10,-10},{10,-10},{-10,10},{10,10}};
        for (int[] c : corners) {
            for (int h = 1; h <= 5; h++) {
                set(level, origin.offset(c[0], h, c[1]),
                        h == 5 ? Blocks.PURPUR_BLOCK.defaultBlockState() : Blocks.END_STONE_BRICKS.defaultBlockState());
            }
            int ix = c[0] > 0 ? -1 : 1;
            int iz = c[1] > 0 ? -1 : 1;
            for (int h = 1; h <= 3; h++) {
                set(level, origin.offset(c[0] + ix, h, c[1]), Blocks.PURPUR_PILLAR.defaultBlockState());
                set(level, origin.offset(c[0], h, c[1] + iz), Blocks.PURPUR_PILLAR.defaultBlockState());
            }
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
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                set(level, origin.offset(x, 2, z), Blocks.OBSIDIAN.defaultBlockState());
            }
        }
        set(level, origin.offset(0, 2, 0), ModBlocks.END_TRIAL_CORE.get().defaultBlockState());
        set(level, origin.offset(0, 3, 0), Blocks.END_ROD.defaultBlockState());
    }

    private void buildWallSegment(WorldGenLevel level, BlockPos base, int along) {
        if (Math.abs(along) <= 2) return;
        for (int h = 0; h < 5; h++) {
            BlockState state;
            if (h == 4) {
                state = (along & 1) == 0 ? Blocks.PURPUR_BLOCK.defaultBlockState() : Blocks.END_STONE_BRICKS.defaultBlockState();
            } else if (h == 2 && Math.abs(along) % 3 == 0) {
                state = Blocks.PURPUR_PILLAR.defaultBlockState();
            } else {
                state = Blocks.END_STONE_BRICKS.defaultBlockState();
            }
            set(level, base.above(h), state);
        }
        if (Math.abs(along) % 4 == 0) {
            set(level, base.above(5), Blocks.PURPUR_SLAB.defaultBlockState());
        }
    }

    private void buildCorridor(WorldGenLevel level, BlockPos origin, int dx, int dz) {
        for (int distance = 10; distance <= 13; distance++) {
            for (int side = -2; side <= 2; side++) {
                int x = dx * distance + (dz != 0 ? side : 0);
                int z = dz * distance + (dx != 0 ? side : 0);
                BlockPos floor = origin.offset(x, 0, z);
                BlockState state = side == 0
                        ? ModBlocks.CHORUS_PLANKS.get().defaultBlockState()
                        : (Math.abs(side) == 2 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).defaultBlockState();
                set(level, floor, state);
                supportFloor(level, floor);
                clearColumn(level, floor, 1, 6);

                if (Math.abs(side) == 2 && distance % 2 == 0) {
                    set(level, floor.above(), Blocks.PURPUR_PILLAR.defaultBlockState());
                    set(level, floor.above(2), Blocks.END_ROD.defaultBlockState());
                }
            }
        }
    }

    private void buildChamber(WorldGenLevel level, BlockPos center, int style, int outwardX, int outwardZ, RandomSource random) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                BlockPos floor = center.offset(x, 0, z);
                int ring = Math.max(Math.abs(x), Math.abs(z));
                BlockState floorState;
                if (ring == 5) {
                    floorState = Blocks.PURPUR_BLOCK.defaultBlockState();
                } else if (((x * 3 + z * 5 + style) & 7) == 0) {
                    floorState = ModBlocks.CHORUS_MOSAIC.get().defaultBlockState();
                } else {
                    floorState = Blocks.END_STONE_BRICKS.defaultBlockState();
                }
                set(level, floor, floorState);
                supportFloor(level, floor);
                clearColumn(level, floor, 1, 7);

                boolean edge = Math.abs(x) == 6 || Math.abs(z) == 6;
                if (!edge || isEntrance(x, z, outwardX, outwardZ)) continue;
                for (int h = 1; h <= 5; h++) {
                    BlockState wall = h == 5
                            ? Blocks.PURPUR_BLOCK.defaultBlockState()
                            : ((h == 3 && ((x + z) & 1) == 0)
                            ? Blocks.PURPUR_PILLAR.defaultBlockState()
                            : Blocks.END_STONE_BRICKS.defaultBlockState());
                    set(level, floor.above(h), wall);
                }
            }
        }

        // Corner towers and roof beams make each side room feel like a real chamber instead of a box.
        int[][] corners = {{-6,-6},{6,-6},{-6,6},{6,6}};
        for (int[] c : corners) {
            for (int h = 1; h <= 7; h++) {
                set(level, center.offset(c[0], h, c[1]),
                        h == 7 ? Blocks.END_ROD.defaultBlockState() : Blocks.PURPUR_PILLAR.defaultBlockState());
            }
        }

        for (int i = -6; i <= 6; i++) {
            BlockState roof = (i & 1) == 0 ? Blocks.PURPUR_SLAB.defaultBlockState() : Blocks.END_STONE_BRICK_SLAB.defaultBlockState();
            set(level, center.offset(i, 6, -6), roof);
            set(level, center.offset(i, 6, 6), roof);
            set(level, center.offset(-6, 6, i), roof);
            set(level, center.offset(6, 6, i), roof);
        }
        for (int i = -4; i <= 4; i++) {
            if ((i & 1) != 0) continue;
            set(level, center.offset(i, 6, 0), Blocks.PURPUR_SLAB.defaultBlockState());
            set(level, center.offset(0, 6, i), Blocks.PURPUR_SLAB.defaultBlockState());
        }

        switch (style) {
            case 0 -> buildChorusPillarRoom(level, center);
            case 1 -> buildObsidianBlinkRoom(level, center);
            case 2 -> buildPurpurStepsRoom(level, center);
            default -> buildCrossfireRoom(level, center, random);
        }
    }

    private boolean isEntrance(int x, int z, int outwardX, int outwardZ) {
        if (outwardX == 1 && x == -6) return Math.abs(z) <= 1;
        if (outwardX == -1 && x == 6) return Math.abs(z) <= 1;
        if (outwardZ == 1 && z == -6) return Math.abs(x) <= 1;
        if (outwardZ == -1 && z == 6) return Math.abs(x) <= 1;
        return false;
    }

    private void buildChorusPillarRoom(WorldGenLevel level, BlockPos center) {
        int[][] points = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] p : points) {
            for (int h = 1; h <= 4; h++) {
                set(level, center.offset(p[0], h, p[1]), ModBlocks.CHORUS_STEM.get().defaultBlockState());
            }
            set(level, center.offset(p[0], 5, p[1]), Blocks.END_ROD.defaultBlockState());
        }
        for (int i = -3; i <= 3; i++) {
            if (i == 0) continue;
            set(level, center.offset(i, 1, 0), ModBlocks.CHORUS_MOSAIC.get().defaultBlockState());
            set(level, center.offset(0, 1, i), ModBlocks.CHORUS_MOSAIC.get().defaultBlockState());
        }
    }

    private void buildObsidianBlinkRoom(WorldGenLevel level, BlockPos center) {
        int[][] points = {{0, -4}, {0, 4}, {-4, 0}, {4, 0}};
        for (int[] p : points) {
            for (int h = 1; h <= 5; h++) {
                set(level, center.offset(p[0], h, p[1]),
                        h == 3 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
            }
            set(level, center.offset(p[0], 6, p[1]), Blocks.END_ROD.defaultBlockState());
        }
        set(level, center.above(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
    }

    private void buildPurpurStepsRoom(WorldGenLevel level, BlockPos center) {
        for (int ring = 0; ring <= 2; ring++) {
            int y = ring + 1;
            int radius = 4 - ring;
            for (int x = -radius; x <= radius; x++) {
                set(level, center.offset(x, y, -radius), Blocks.PURPUR_BLOCK.defaultBlockState());
                set(level, center.offset(x, y, radius), Blocks.PURPUR_BLOCK.defaultBlockState());
            }
            for (int z = -radius + 1; z < radius; z++) {
                set(level, center.offset(-radius, y, z), Blocks.PURPUR_BLOCK.defaultBlockState());
                set(level, center.offset(radius, y, z), Blocks.PURPUR_BLOCK.defaultBlockState());
            }
        }
        set(level, center.offset(0, 4, 0), Blocks.END_ROD.defaultBlockState());
    }

    private void buildCrossfireRoom(WorldGenLevel level, BlockPos center, RandomSource random) {
        for (int i = -4; i <= 4; i += 2) {
            int offset = random.nextBoolean() ? 2 : -2;
            set(level, center.offset(i, 1, offset), ModBlocks.CHORUS_MOSAIC.get().defaultBlockState());
            set(level, center.offset(offset, 1, i), Blocks.PURPUR_PILLAR.defaultBlockState());
            set(level, center.offset(i, 2, offset), Blocks.PURPUR_SLAB.defaultBlockState());
        }
        set(level, center.above(), Blocks.END_ROD.defaultBlockState());
        set(level, center.offset(0, 2, 0), Blocks.OBSIDIAN.defaultBlockState());
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
