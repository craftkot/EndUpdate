package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Experimental REAL moving creature: a tiny isolated connected group of
 * already infected vanilla blocks relocates as a single rigid mass.
 * No entities, fabricated blocks, flying parts, or disconnected pieces.
 *
 * Unlike the smooth LEAF client rig, a world-cell relocation is currently
 * discrete (one cell every 12 ticks). Larger colonies and structures are
 * intentionally refused until a dedicated smooth mobile render rig exists.
 */
public final class WalkingColony {
    private static final int MAX_PARTS = 8, MAX_STEPS = 10, STEP_TICKS = 12;
    private static final Map<ServerLevel, List<Walk>> ACTIVE = new WeakHashMap<>();
    private record Piece(BlockPos pos, BlockState state) {}
    private static final class Walk {
        final UUID target;
        final List<Piece> pieces;
        long nextStep;
        int steps;
        int blocked;
        boolean struck;
        Walk(UUID target, List<Piece> pieces, long time) {
            this.target = target; this.pieces = pieces; this.nextStep = time + STEP_TICKS;
        }
        Vec3 center() {
            double x=0, y=0, z=0;
            for (Piece p : pieces) {
                x += p.pos.getX()+.5;
                y += p.pos.getY()+.5;
                z += p.pos.getZ()+.5;
            }
            int n=pieces.size();
            return new Vec3(x/n,y/n,z/n);
        }
    }

    private WalkingColony() {}

    public static int activeCount(ServerLevel level) {
        return ACTIVE.getOrDefault(level, List.of()).size();
    }

    /**
     * Detailed operator-facing failure report. Unlike the previous single
     * generic error, this names the first violated constraint and its location.
     * A null result means the shape is ready to walk.
     */
    public static String diagnose(ServerLevel level, BlockPos origin) {
        if (activeCount(level) >= 4) return "Already 4 walking creatures in this dimension.";
        if (!level.hasChunkAt(origin)) return "Target chunk is not loaded.";
        MimicData infected = MimicData.get(level);
        if (!infected.isInfected(origin))
            return "Target is not infected. Use /livingmatter infect or /livingmatter walktest.";
        if (!MimicData.eligible(level, origin)) return "Target block cannot be infected.";
        MimicKind kind = MimicKind.of(level.getBlockState(origin));
        if (kind != MimicKind.STONE && kind != MimicKind.WOOD
            && kind != MimicKind.ORE && kind != MimicKind.WILD)
            return "This material (" + kind.name() + ") cannot walk yet. Use stone, wood or ore.";
        List<BlockPos> body = BrainData.get(level).bodyAt(level, origin, MAX_PARTS + 1);
        if (body.size() < 3)
            return "Only " + body.size() + " connected infected " + kind.name()
                + " blocks found; need at least 3. Aim at an isolated 2x2 structure.";
        if (body.size() > MAX_PARTS)
            return "Detected 9+ connected infected blocks; the prototype supports at most 8.";
        for (Walk walking : ACTIVE.getOrDefault(level, List.of()))
            for (Piece piece : walking.pieces)
                if (body.contains(piece.pos)) return "Those blocks are already walking.";
        Set<BlockPos> own = new HashSet<>(body);
        for (BlockPos pos : body) {
            if (BrainData.get(level).isBrain(pos))
                return "The organism contains the brain at " + pos.toShortString()
                    + ". Build its body away from the brain.";
            BlockState state = level.getBlockState(pos);
            if (!state.isCollisionShapeFullBlock(level, pos)
                || state.hasBlockEntity() || !state.getFluidState().isEmpty())
                return "Body block " + pos.toShortString()
                    + " is not a solid full cube (or has inventory/fluid).";
            for (Direction direction : Direction.values()) {
                if (direction == Direction.DOWN) continue;
                BlockPos adjacent = pos.relative(direction);
                if (!own.contains(adjacent) && (!level.hasChunkAt(adjacent)
                    || !level.getBlockState(adjacent).isAir()))
                    return "Body touches other blocks at " + adjacent.toShortString()
                        + " (" + direction.name() + "). Clear the sides and top; keep only supporting ground.";
            }
            BlockPos below = pos.below();
            if (!own.contains(below)
                && (!level.hasChunkAt(below)
                    || !level.getBlockState(below).isCollisionShapeFullBlock(level, below)))
                return "No solid supporting ground under " + pos.toShortString() + ".";
        }
        return null;
    }

    /**
     * Explicit creative/admin test: choose an isolated existing 3-8 block
     * structure and infect it automatically. Does NOT spawn blocks, remove
     * structures or seize terrain attached to a wall or tree.
     */
    public static String prepareAndWalk(ServerLevel level, BlockPos origin, ServerPlayer target) {
        if (!level.hasChunkAt(origin) || !MimicData.eligible(level, origin))
            return "Look at an existing sturdy block in a loaded chunk.";
        if (BrainData.get(level).isBrain(origin))
            return "The selected block is a brain; point to a separate structure instead.";
        BlockState first = level.getBlockState(origin);
        MimicKind kind = MimicKind.of(first);
        if (kind != MimicKind.STONE && kind != MimicKind.WOOD
            && kind != MimicKind.ORE && kind != MimicKind.WILD)
            return "Walktest currently supports solid stone, wood, ore and fallback materials.";
        Set<BlockPos> found = new HashSet<>();
        java.util.ArrayDeque<BlockPos> pending = new java.util.ArrayDeque<>();
        found.add(origin.immutable());
        pending.add(origin.immutable());
        while (!pending.isEmpty() && found.size() <= MAX_PARTS) {
            BlockPos position = pending.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = position.relative(direction);
                if (found.contains(next) || !level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).getBlock() == first.getBlock()) {
                    found.add(next.immutable());
                    pending.addLast(next.immutable());
                }
            }
        }
        if (found.size() < 3)
            return "Only " + found.size() + " matching blocks connected. Build a 2x2 shape (4 blocks).";
        if (found.size() > MAX_PARTS)
            return "Structure joins 9+ blocks of the same material. Use a freestanding 2x2 shape on a DIFFERENT ground material.";
        for (BlockPos pos : found) {
            if (BrainData.get(level).isBrain(pos))
                return "The structure contains a brain at " + pos.toShortString() + ".";
            BlockState state = level.getBlockState(pos);
            if (!MimicData.eligible(level, pos)
                || !state.isCollisionShapeFullBlock(level, pos))
                return "Unsafe/non-full block at " + pos.toShortString() + ".";
            for (Direction direction : Direction.values()) {
                if (direction == Direction.DOWN) continue;
                BlockPos adjacent = pos.relative(direction);
                if (!found.contains(adjacent)
                    && (!level.hasChunkAt(adjacent)
                        || !level.getBlockState(adjacent).isAir()))
                    return "Structure is still attached at " + adjacent.toShortString()
                        + ". Clear its sides and top.";
            }
            BlockPos below = pos.below();
            if (!found.contains(below)
                && (!level.hasChunkAt(below)
                    || !level.getBlockState(below).isCollisionShapeFullBlock(level, below)))
                return "The shape needs solid ground below " + pos.toShortString() + ".";
        }
        MimicData data = MimicData.get(level);
        // All world/structure checks passed before changing infection markers.
        for (BlockPos pos : found) {
            if (!data.mark(level, pos)) return "Failed to infect " + pos.toShortString() + ".";
        }
        if (tryStart(level, origin, target)) return null;
        String cause = diagnose(level, origin);
        return cause == null ? "Could not start (another walking organism may be active)." : cause;
    }

    public static boolean tryStart(ServerLevel level, BlockPos origin, ServerPlayer target) {
        if (diagnose(level, origin) != null) return false;
        MimicData infection = MimicData.get(level);
        if (!infection.isInfected(origin) || !MimicData.eligible(level, origin)) return false;
        MimicKind kind = MimicKind.of(level.getBlockState(origin));
        if (kind != MimicKind.STONE && kind != MimicKind.WOOD
            && kind != MimicKind.ORE && kind != MimicKind.WILD) return false;
        // Existing connected component only; refuse sprawling colonies.
        List<BlockPos> body = BrainData.get(level).bodyAt(level, origin, MAX_PARTS + 1);
        if (body.size() < 3 || body.size() > MAX_PARTS) return false;
        for (Walk walking : ACTIVE.getOrDefault(level, List.of())) {
            for (Piece piece : walking.pieces)
                if (body.contains(piece.pos)) return false;
        }
        Set<BlockPos> own = new HashSet<>(body);
        List<Piece> parts = new ArrayList<>();
        for (BlockPos pos : body) {
            BlockState state = level.getBlockState(pos);
            if (BrainData.get(level).isBrain(pos)
                || !state.isCollisionShapeFullBlock(level, pos)
                || state.hasBlockEntity() || !state.getFluidState().isEmpty())
                return false;
            // An organism is allowed to rest on normal ground, but cannot tear
            // pieces out of walls, houses, trees or other attached matter.
            for (Direction d : Direction.values()) {
                if (d == Direction.DOWN) continue; // supporting ground
                BlockPos adjacent = pos.relative(d);
                if (!own.contains(adjacent)
                    && (!level.hasChunkAt(adjacent)
                    || !level.getBlockState(adjacent).isAir())) return false;
            }
            parts.add(new Piece(pos.immutable(), state));
        }
        // Every moving part belongs to the same ORIGINAL connected body.
        List<Walk> active = ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>());
        active.add(new Walk(target.getUUID(), parts, level.getGameTime()));
        level.playSound(null, origin, SoundEvents.CHORUS_FLOWER_GROW,
            SoundSource.HOSTILE, 1.0F, .55F);
        return true;
    }

    public static void tick(ServerLevel level) {
        List<Walk> active = ACTIVE.get(level);
        if (active == null || active.isEmpty()) return;
        Iterator<Walk> iterator = active.iterator();
        while (iterator.hasNext()) {
            Walk walker = iterator.next();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(walker.target);
            if (target == null || !target.isAlive() || target.level() != level
                || walker.steps >= MAX_STEPS) {
                iterator.remove();
                continue;
            }
            for (Piece piece : walker.pieces)
                if (!level.hasChunkAt(piece.pos) || !level.getBlockState(piece.pos).is(piece.state.getBlock())) {
                    iterator.remove();
                    target = null;
                    break;
                }
            if (target == null) continue;
            Vec3 middle = walker.center();
            if (target.position().distanceToSqr(middle) < 5.0) {
                if (!walker.struck && !target.isCreative() && !target.isSpectator()) {
                    walker.struck = true;
                    target.hurtServer(level, level.damageSources().cactus(), 5.0F);
                    level.playSound(null, walker.pieces.get(0).pos,
                        SoundEvents.SHULKER_BULLET_HIT, SoundSource.HOSTILE, .9F, .6F);
                }
                iterator.remove();
                continue;
            }
            if (target.distanceToSqr(middle) > 18 * 18 || target.isSpectator()) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < walker.nextStep) continue;
            walker.nextStep = level.getGameTime() + STEP_TICKS;
            List<Direction> directions = new ArrayList<>(List.of(
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
            final ServerPlayer destinationTarget = target;
            directions.sort(Comparator.comparingDouble(d -> middle.add(d.getStepX(), 0, d.getStepZ())
                .distanceToSqr(destinationTarget.position())));
            boolean moved = false;
            for (Direction dir : directions) {
                Vec3 destination = middle.add(dir.getStepX(), 0, dir.getStepZ());
                if (destination.distanceToSqr(target.position())
                    >= middle.distanceToSqr(target.position()) - .01) continue;
                if (moveAsOne(level, walker, dir, target)) {
                    moved = true; break;
                }
            }
            if (!moved && ++walker.blocked > 3) iterator.remove();
            if (moved) {
                walker.steps++;
                walker.blocked = 0;
            }
        }
    }

    private static boolean moveAsOne(ServerLevel level, Walk walker,
                                     Direction dir, ServerPlayer target) {
        LinkedHashMap<BlockPos, BlockState> originals = new LinkedHashMap<>();
        Set<BlockPos> oldPositions = new HashSet<>();
        Set<BlockPos> destination = new HashSet<>();
        Map<BlockPos, BlockPos> motion = new LinkedHashMap<>();
        for (Piece part : walker.pieces) {
            originals.put(part.pos, part.state);
            oldPositions.add(part.pos);
            BlockPos to = part.pos.relative(dir);
            motion.put(part.pos, to);
            destination.add(to);
        }
        for (BlockPos to : destination) {
            if (!level.hasChunkAt(to) || to.getY() < level.getMinY()
                || to.getY() >= level.getMaxY()) return false;
            if (!oldPositions.contains(to) && !level.getBlockState(to).isAir()) return false;
            // Whole-body support: every bottom block has either another
            // organism block immediately beneath or a solid world floor.
            BlockPos below = to.below();
            if (!destination.contains(below)
                && (!level.hasChunkAt(below)
                    || !level.getBlockState(below).isCollisionShapeFullBlock(level,below)))
                return false;
            if (target.getBoundingBox().intersects(new AABB(to))) return false;
        }
        // All destinations validated: remove the old blocks as one operation.
        for (BlockPos old : oldPositions)
            level.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
        for (Piece part : walker.pieces) {
            BlockPos to = motion.get(part.pos);
            if (!level.setBlock(to, part.state, 3)) {
                // Best effort rollback, without overwriting third-party blocks.
                for (BlockPos pos : destination)
                    if (level.getBlockState(pos).getBlock() == part.state.getBlock())
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                for (var original : originals.entrySet())
                    if (level.getBlockState(original.getKey()).isAir())
                        level.setBlock(original.getKey(), original.getValue(), 3);
                return false;
            }
        }
        MimicData.get(level).transferBody(motion);
        BrainData.get(level).transferBody(motion);
        // The world moves atomically one grid step; render interpolation uses
        // the original block materials and the exact same transform for every
        // existing part. No separate particles/entities represent the body.
        List<BlockPos> before = new ArrayList<>(originals.keySet());
        List<String> textures = new ArrayList<>();
        for (BlockState state : originals.values()) {
            textures.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }
        Vec3 origin = walker.center();
        PacketDistributor.sendToPlayersNear(level, null,
            origin.x,origin.y,origin.z,64,
            new MatterWalkPacket(List.copyOf(before),List.copyOf(textures),
                (byte)dir.getStepX(),(byte)dir.getStepZ(),STEP_TICKS));
        List<Piece> shifted = new ArrayList<>();
        for (Piece part : walker.pieces) shifted.add(new Piece(motion.get(part.pos), part.state));
        walker.pieces.clear();
        walker.pieces.addAll(shifted);
        level.playSound(null, shifted.get(0).pos, SoundEvents.CHORUS_FLOWER_GROW,
            SoundSource.HOSTILE, .3F, .4F);
        return true;
    }
}
