package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Tree-borne foliage hunts by reusing EXISTING vanilla leaf blocks.
 * A leaf from the edge of the crown is moved to an empty position adjacent
 * to the attached tip; each new position touches the old tip face-to-face.
 *
 * A canopy donor is never chosen if removing it would disconnect the scanned
 * tree crown. No new foliage is generated, and no flying blocks are used.
 * Every move is journaled for reversal once the short attack finishes.
 */
public final class LeafAmbush {
    private static final int MAX_REACH = 8;
    private static final int MAX_EXTENSION = 7;
    private static final int MAX_TREE_LEAVES = 160;
    private static final int TICK_STEP = 6;
    private static final int LIFE_TICKS = 105;
    private static final Map<ServerLevel, Map<Long, Hunt>> HUNTS = new WeakHashMap<>();

    private record Move(BlockPos from, BlockPos to, BlockState original, BlockState moving) {}

    private static final class Hunt {
        final BlockPos origin;
        final UUID victim;
        final long started;
        final List<Move> moved = new ArrayList<>();
        BlockPos tip;
        int hitCount;
        int misses;

        Hunt(BlockPos origin, UUID victim, long started) {
            this.origin = origin.immutable();
            this.victim = victim;
            this.started = started;
            this.tip = origin.immutable();
        }
    }

    private LeafAmbush() {}

    public static boolean isHunting(ServerLevel level, ServerPlayer player) {
        for (Hunt hunt : HUNTS.getOrDefault(level, Map.of()).values()) {
            if (hunt.victim.equals(player.getUUID())) return true;
        }
        return false;
    }

    public static boolean tryBegin(ServerLevel level, BlockPos origin, ServerPlayer target, boolean forced) {
        if (!level.hasChunkAt(origin) || !level.getBlockState(origin).is(BlockTags.LEAVES)
            || !MimicData.get(level).isInfected(origin) || !MimicData.eligible(level, origin)
            || (!forced && (target.isCreative() || target.isSpectator()))
            || target.distanceToSqr(Vec3.atCenterOf(origin)) > (MAX_REACH + 1) * (MAX_REACH + 1))
            return false;
        Map<Long, Hunt> hunts = HUNTS.computeIfAbsent(level, unused -> new HashMap<>());
        if (hunts.containsKey(origin.asLong()) || isHunting(level, target)) return false;
        Set<BlockPos> crown = collectLeaves(level, origin, Set.of());
        if (crown.size() < 10 || !hasTrunk(level, crown)) return false;

        hunts.put(origin.asLong(), new Hunt(origin, target.getUUID(), level.getGameTime()));
        level.playSound(null, origin, SoundEvents.CHORUS_FLOWER_GROW, SoundSource.HOSTILE,
            0.6F, 1.35F);
        return true;
    }

    public static int activeCount(ServerLevel level) {
        return HUNTS.getOrDefault(level, Map.of()).size();
    }

    public static void tick(ServerLevel level) {
        Map<Long, Hunt> hunts = HUNTS.get(level);
        if (hunts == null || hunts.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Hunt> iterator = hunts.values().iterator();
        while (iterator.hasNext()) {
            Hunt hunt = iterator.next();
            if (!loaded(level, hunt)) continue; // do not force-load chunks for cleanup
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(hunt.victim);
            boolean valid = target != null && target.isAlive() && target.level() == level
                && !target.isSpectator() && level.getBlockState(hunt.origin).is(BlockTags.LEAVES)
                && MimicData.get(level).isInfected(hunt.origin);
            if (!valid || now - hunt.started > LIFE_TICKS
                || (target != null && target.distanceToSqr(Vec3.atCenterOf(hunt.origin)) > 160)) {
                restore(level, hunt);
                iterator.remove();
                continue;
            }
            if ((now - hunt.started) % TICK_STEP != 0) continue;

            Vec3 targetPoint = target.getEyePosition();
            double distance = Vec3.atCenterOf(hunt.tip).distanceTo(targetPoint);
            if (distance <= 2.25) {
                // Existing foliage grips the player and pulls it toward its trunk.
                if (!target.isCreative() && hunt.hitCount < 2) {
                    boolean hit = target.hurtServer(level, level.damageSources().cactus(), 2.0F);
                    if (hit) {
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 2));
                        Vec3 towardTree = Vec3.atCenterOf(hunt.origin).subtract(target.position());
                        if (towardTree.lengthSqr() > .01) {
                            Vec3 pull = towardTree.normalize().scale(.23);
                            target.push(pull.x, .06, pull.z);
                        }
                        level.sendParticles(ParticleTypes.CRIT, target.getX(),
                            target.getY() + 1.0, target.getZ(), 8, .25, .4, .25, .04);
                        level.playSound(null, hunt.tip, SoundEvents.SHULKER_BULLET_HIT,
                            SoundSource.HOSTILE, .8F, 1.3F);
                    }
                    hunt.hitCount++;
                }
                if (hunt.hitCount >= 2 || now - hunt.started > 72) {
                    restore(level, hunt);
                    iterator.remove();
                }
                continue;
            }

            if (hunt.moved.size() >= MAX_EXTENSION) continue;
            BlockPos next = findNext(level, hunt, target);
            if (next == null) {
                if (++hunt.misses > 4) {
                    restore(level, hunt);
                    iterator.remove();
                }
                continue;
            }
            BlockPos donor = findSafeDonor(level, hunt);
            if (donor == null) {
                restore(level, hunt);
                iterator.remove();
                continue;
            }
            BlockState original = level.getBlockState(donor);
            BlockState moving = original;
            // A detached-from-log segment must not decay during the attack.
            if (moving.hasProperty(LeavesBlock.PERSISTENT))
                moving = moving.setValue(LeavesBlock.PERSISTENT, true);

            // Destination is verified first; no existing terrain is overwritten.
            if (!level.getBlockState(next).isAir()) continue;
            if (!level.setBlock(donor, Blocks.AIR.defaultBlockState(), 3)) continue;
            if (!level.setBlock(next, moving, 3)) {
                if (level.getBlockState(donor).isAir()) level.setBlock(donor, original, 3);
                continue;
            }
            hunt.moved.add(new Move(donor.immutable(), next.immutable(), original, moving));
            hunt.tip = next.immutable();
            level.sendParticles(ParticleTypes.CRIT, next.getX() + .5, next.getY() + .5,
                next.getZ() + .5, 3, .15, .15, .15, .005);
            level.playSound(null, next, SoundEvents.CHORUS_FLOWER_GROW,
                SoundSource.BLOCKS, .25F, 1.75F);
        }
    }

    private static boolean loaded(ServerLevel level, Hunt hunt) {
        if (!level.hasChunkAt(hunt.origin)) return false;
        for (Move move : hunt.moved) {
            if (!level.hasChunkAt(move.from) || !level.hasChunkAt(move.to)) return false;
        }
        return true;
    }

    private static void restore(ServerLevel level, Hunt hunt) {
        // Undo in reverse order and only if no player/world edit took ownership
        // of a destination. Never overwrite another material.
        for (int i = hunt.moved.size() - 1; i >= 0; i--) {
            Move move = hunt.moved.get(i);
            BlockState placed = level.getBlockState(move.to);
            if (placed.getBlock() == move.moving.getBlock())
                level.setBlock(move.to, Blocks.AIR.defaultBlockState(), 3);
            if (level.getBlockState(move.from).isAir())
                level.setBlock(move.from, move.original, 3);
        }
    }

    private static BlockPos findNext(ServerLevel level, Hunt hunt, ServerPlayer target) {
        Vec3 end = target.getEyePosition();
        double previous = Vec3.atCenterOf(hunt.tip).distanceToSqr(end);
        List<BlockPos> positions = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos next = hunt.tip.relative(direction);
            if (!level.hasChunkAt(next) || !level.getBlockState(next).isAir()) continue;
            if (next.distSqr(hunt.origin) > (long) MAX_REACH * MAX_REACH) continue;
            if (target.getBoundingBox().intersects(new AABB(next))) continue;
            // The new leaf must actually get closer; it always shares a full
            // block face with the previously existing tip.
            if (Vec3.atCenterOf(next).distanceToSqr(end) < previous - .05)
                positions.add(next);
        }
        return positions.stream()
            .min(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(end)))
            .orElse(null);
    }

    private static BlockPos findSafeDonor(ServerLevel level, Hunt hunt) {
        Set<BlockPos> extensions = new HashSet<>();
        for (Move move : hunt.moved) extensions.add(move.to);
        Set<BlockPos> crown = collectLeaves(level, hunt.origin, extensions);
        if (crown.size() < 9 || !hasTrunk(level, crown)) return null;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos leaf : crown) {
            if (leaf.equals(hunt.origin) || MimicData.get(level).isInfected(leaf)) continue;
            if (leaf.distSqr(hunt.origin) < 4L) continue;
            boolean used = false;
            for (Move move : hunt.moved) {
                if (move.from.equals(leaf)) { used = true; break; }
            }
            if (used) continue;
            int degree = 0;
            for (Direction dir : Direction.values())
                if (crown.contains(leaf.relative(dir))) degree++;
            if (degree > 3) continue; // use only outer, expendable foliage
            candidates.add(leaf);
        }
        // Prefer loosely attached outer leaves rather than hollowing the crown.
        candidates.sort(Comparator.<BlockPos>comparingInt(pos -> neighbours(crown, pos))
            .thenComparing(Comparator.comparingLong((BlockPos pos) -> pos.distSqr(hunt.origin)).reversed()));
        for (BlockPos candidate : candidates) {
            if (remainsConnected(crown, hunt.origin, candidate)) return candidate;
        }
        return null;
    }

    private static int neighbours(Set<BlockPos> positions, BlockPos pos) {
        int found = 0;
        for (Direction dir : Direction.values())
            if (positions.contains(pos.relative(dir))) found++;
        return found;
    }

    private static boolean remainsConnected(Set<BlockPos> crown, BlockPos root, BlockPos removed) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(root);
        seen.add(root);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!next.equals(removed) && crown.contains(next) && seen.add(next))
                    queue.add(next);
            }
        }
        return seen.size() == crown.size() - 1;
    }

    private static boolean hasTrunk(ServerLevel level, Set<BlockPos> crown) {
        for (BlockPos leaf : crown) {
            for (Direction dir : Direction.values()) {
                BlockPos near = leaf.relative(dir);
                if (level.hasChunkAt(near) && level.getBlockState(near).is(BlockTags.LOGS))
                    return true;
            }
        }
        return false;
    }

    /** Load-safe bounded six-connected canopy search; no new chunks loaded. */
    private static Set<BlockPos> collectLeaves(ServerLevel level, BlockPos origin, Set<BlockPos> exclude) {
        Set<BlockPos> result = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        result.add(origin);
        while (!queue.isEmpty() && result.size() < MAX_TREE_LEAVES) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (exclude.contains(next) || result.contains(next)
                    || next.distSqr(origin) > 80L || !level.hasChunkAt(next)
                    || !level.getBlockState(next).is(BlockTags.LEAVES)) continue;
                result.add(next);
                queue.addLast(next);
                if (result.size() >= MAX_TREE_LEAVES) break;
            }
        }
        return result;
    }
}
