package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
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

    public static boolean tryStart(ServerLevel level, BlockPos origin, ServerPlayer target) {
        if (activeCount(level) >= 4 || !level.hasChunkAt(origin)) return false;
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
            directions.sort(Comparator.comparingDouble(d -> middle.add(d.getStepX(), 0, d.getStepZ())
                .distanceToSqr(target.position())));
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
        List<Piece> shifted = new ArrayList<>();
        for (Piece part : walker.pieces) shifted.add(new Piece(motion.get(part.pos), part.state));
        walker.pieces.clear();
        walker.pieces.addAll(shifted);
        level.playSound(null, shifted.get(0).pos, SoundEvents.CHORUS_FLOWER_GROW,
            SoundSource.HOSTILE, .3F, .4F);
        return true;
    }
}
