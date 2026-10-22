package com.craftkot.livingmatter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent, material-agnostic infection. A brain is an ACTUAL vanilla block,
 * not a new replacement block or entity; its world coordinate is the colony
 * origin. Every infected position is reached by adjacency to the colony.
 * World block states are left intact while dormant.
 */
public final class BrainData extends SavedData {
    private static final int MAX_CORES = 32;
    private static final int MAX_MEMBERS = 180;
    private static final int MAX_RADIUS_SQ = 144;
    private static final int GROW_EVERY = 20; // one potential extension per second

    public record Colony(long core, List<Long> members) {
        public static final Codec<Colony> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("core").forGetter(Colony::core),
            Codec.LONG.listOf().fieldOf("members").forGetter(Colony::members)
        ).apply(i, Colony::new));
    }

    public static final SavedDataType<BrainData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(LivingMatter.MOD_ID, "brains"),
        BrainData::new,
        RecordCodecBuilder.create(i -> i.group(
            Colony.CODEC.listOf().fieldOf("colonies")
                .forGetter(d -> d.snapshot())
        ).apply(i, BrainData::new))
    );

    private final ArrayList<MutableColony> colonies = new ArrayList<>();
    private long lastTick = Long.MIN_VALUE;

    private static final class MutableColony {
        final long origin;
        final Set<Long> cells;
        MutableColony(Colony colony) {
            this.origin = colony.core();
            this.cells = new HashSet<>(colony.members());
            this.cells.add(origin);
        }
    }

    public BrainData() {}
    private BrainData(List<Colony> stored) {
        for (Colony c : stored) if (colonies.size() < MAX_CORES)
            colonies.add(new MutableColony(c));
    }
    private List<Colony> snapshot() {
        return colonies.stream()
            .map(c -> new Colony(c.origin, new ArrayList<>(c.cells))).toList();
    }
    public static BrainData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
    public int brainCount() { return colonies.size(); }
    public int memberCount() { return colonies.stream().mapToInt(c -> c.cells.size()).sum(); }
    public boolean isBrain(BlockPos pos) {
        for (MutableColony c : colonies) if (c.origin == pos.asLong()) return true;
        return false;
    }

    public boolean seed(ServerLevel level, BlockPos position) {
        if (!MimicData.eligible(level, position)) return false;
        if (isBrain(position)) return true;
        if (colonies.size() >= MAX_CORES) return false;
        if (colonies.stream().anyMatch(c -> BlockPos.of(c.origin).distSqr(position) < 196))
            return false; // one nervous system per local biome-scale patch
        if (!MimicData.get(level).mark(level, position)) return false;
        colonies.add(new MutableColony(new Colony(position.asLong(), List.of(position.asLong()))));
        setDirty();
        level.sendParticles(ParticleTypes.PORTAL,
            position.getX() + .5, position.getY() + .5, position.getZ() + .5,
            18, .3, .3, .3, .025);
        return true;
    }

    public boolean remove(BlockPos core) {
        boolean changed = colonies.removeIf(c -> c.origin == core.asLong());
        if (changed) setDirty();
        return changed;
    }

    /**
     * Natural brains are VERY rare and deterministic; nearby-player scans do
     * not reroll the same position indefinitely. Operator placement is preferred
     * for testing: /livingmatter brain.
     */
    public void discover(ServerLevel level, BlockPos pos) {
        if (brainCount() >= MAX_CORES || !MimicData.eligible(level, pos)) return;
        long seed = level.getSeed() ^ pos.asLong() ^ 0x425241494E534545L;
        long mixed = mix64(seed);
        if ((mixed & 0xFFFFFL) == 17L) seed(level, pos);
    }

    public void tick(ServerLevel level) {
        long time = level.getGameTime();
        if (time % GROW_EVERY != 0 || lastTick == time) return;
        lastTick = time;
        MimicData infection = MimicData.get(level);
        boolean modified = false;
        for (int index = 0; index < colonies.size(); index++) {
            MutableColony colony = colonies.get(index);
            BlockPos root = BlockPos.of(colony.origin);
            if (!level.hasChunkAt(root)) continue;
            if (!MimicData.eligible(level, root) || !infection.isInfected(root)) {
                colonies.remove(index--);
                modified = true;
                continue; // breaking the brain halts growth; existing infected blocks remain
            }
            if (colony.cells.size() >= MAX_MEMBERS) continue;

            // Reconcile externally destroyed leaf/stone chunks. Iterate only
            // the bounded membership of this one colony (at most 180).
            if (colony.cells.removeIf(p -> {
                BlockPos at = BlockPos.of(p);
                return !at.equals(root) && level.hasChunkAt(at)
                    && !MimicData.eligible(level, at);
            })) modified = true;

            ArrayList<BlockPos> boundary = new ArrayList<>();
            for (long key : colony.cells) {
                BlockPos old = BlockPos.of(key);
                if (!level.hasChunkAt(old)) continue;
                for (Direction face : Direction.values()) {
                    BlockPos neighbour = old.relative(face);
                    if (colony.cells.contains(neighbour.asLong())
                        || neighbour.distSqr(root) > MAX_RADIUS_SQ
                        || !level.hasChunkAt(neighbour)
                        || !MimicData.eligible(level, neighbour)) continue;
                    boundary.add(neighbour);
                }
            }
            if (boundary.isEmpty()) continue;
            // Slow, organic growth; each core tries to infect a single
            // adjoining existing solid block every second, never air.
            BlockPos target = boundary.get(level.getRandom().nextInt(boundary.size()));
            if (infection.mark(level, target) && colony.cells.add(target.asLong())) {
                modified = true;
                if (level.getRandom().nextInt(4) == 0)
                    level.sendParticles(ParticleTypes.PORTAL, target.getX() + .5,
                        target.getY() + .5, target.getZ() + .5, 4, .15, .15, .15, .01);
            }
        }
        if (modified) setDirty();
    }

    public int localCount(BlockPos around, int radius) {
        long square = (long)radius * radius;
        int sum = 0;
        for (MutableColony c : colonies)
            for (long p : c.cells)
                if (BlockPos.of(p).distSqr(around) <= square) sum++;
        return sum;
    }

    /**
     * A cluster suitable for a moving body is a connected subset of existing,
     * infected, matching material blocks. Return the candidate coordinates;
     * the motion layer can animate them without inventing new blocks.
     */
    public List<BlockPos> bodyAt(ServerLevel level, BlockPos origin, int limit) {
        if (!MimicData.get(level).isInfected(origin) || !MimicData.eligible(level, origin))
            return List.of();
        var originalKind = MimicKind.of(level.getBlockState(origin));
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> found = new HashSet<>();
        queue.add(origin);
        found.add(origin.asLong());
        ArrayList<BlockPos> body = new ArrayList<>();
        while (!queue.isEmpty() && body.size() < Math.min(limit, 20)) {
            BlockPos pos = queue.removeFirst();
            if (!level.hasChunkAt(pos) || !MimicData.get(level).isInfected(pos)
                || !MimicData.eligible(level, pos)
                || MimicKind.of(level.getBlockState(pos)) != originalKind) continue;
            body.add(pos.immutable());
            for (Direction face : Direction.values()) {
                BlockPos next = pos.relative(face);
                if (found.add(next.asLong()) && next.distSqr(origin) <= 80)
                    queue.addLast(next);
            }
        }
        return List.copyOf(body);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
