package com.craftkot.livingchorus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-only, per-dimension hidden markers: the actual world block remains
 * exactly minecraft:chorus_flower, retaining the vanilla block shape, item ID,
 * client blockstate and minimap appearance.
 */
public final class MimicData extends SavedData {
    public static final SavedDataType<MimicData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(LivingChorus.MOD_ID, "hidden_mimic_flowers"),
        MimicData::new,
        RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().fieldOf("infected").forGetter(data -> new ArrayList<>(data.infected)),
            Codec.LONG.listOf().fieldOf("safe").forGetter(data -> new ArrayList<>(data.safe)),
            Codec.LONG.listOf().optionalFieldOf("stems", List.of())
                .forGetter(data -> new ArrayList<>(data.infectedStems)),
            Codec.LONG.listOf().optionalFieldOf("growthOrigins", List.of())
                .forGetter(data -> new ArrayList<>(data.growthOrigins))
        ).apply(instance, MimicData::new))
    );

    private static final int INFECTION_THRESHOLD = 3277; // approx. 5% of eligible flowers
    private final Set<Long> infected;
    private final Set<Long> safe;
    private final Set<Long> infectedStems;
    // A former infected flower which became a stem during vanilla growth.
    // Stored separately so infection survives the gap before new tips appear.
    private final Set<Long> growthOrigins;

    public MimicData() {
        this(List.of(), List.of(), List.of(), List.of());
    }

    private MimicData(List<Long> infected, List<Long> safe, List<Long> stems,
                      List<Long> growthOrigins) {
        this.infected = new HashSet<>(infected);
        this.safe = new HashSet<>(safe);
        this.infectedStems = new HashSet<>(stems);
        this.growthOrigins = new HashSet<>(growthOrigins);
    }

    public static MimicData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isInfected(BlockPos pos) {
        return infected.contains(pos.asLong());
    }

    public int count() {
        return infected.size();
    }

    public int infectedStemCount() {
        return infectedStems.size();
    }

    public int growingOriginCount() {
        return growthOrigins.size();
    }

    /**
     * Vanilla flower growth replaces the old chorus_flower with chorus_plant.
     * Transfer the infection marker to that new stem rather than forgetting it.
     * This method must also be used in the AI's stale-block checks, because
     * those may run earlier than the periodic infection spread task.
     */
    public void reconcileFlower(ServerLevel level, BlockPos pos) {
        long id = pos.asLong();
        if (!infected.contains(id) || !level.hasChunkAt(pos)) return;
        if (level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) return;
        boolean changed = infected.remove(id);
        if (level.getBlockState(pos).is(Blocks.CHORUS_PLANT)) {
            changed |= infectedStems.add(id);
            changed |= growthOrigins.add(id);
            // Growing sideways buds can be adjacent on the very same tick.
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos bud = pos.relative(direction);
                if (level.hasChunkAt(bud)
                    && level.getBlockState(bud).is(Blocks.CHORUS_FLOWER)
                    && !safe.contains(bud.asLong()))
                    changed |= infected.add(bud.asLong());
            }
            // Growth source is now tracked; old stem is no longer a flower.
            if (hasInfectedAdjacentFlower(level, pos)) changed |= growthOrigins.remove(id);
        }
        if (changed) setDirty();
    }

    private boolean hasInfectedAdjacentFlower(ServerLevel level, BlockPos stem) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos bud = stem.relative(direction);
            if (infected.contains(bud.asLong())
                && level.hasChunkAt(bud) && level.getBlockState(bud).is(Blocks.CHORUS_FLOWER))
                return true;
        }
        return false;
    }

    /** Examine actual infected flowers before any other code discards them. */
    public void reconcileAllGrowth(ServerLevel level) {
        for (Long id : new ArrayList<>(infected)) {
            BlockPos pos = BlockPos.of(id);
            if (level.hasChunkAt(pos)) reconcileFlower(level, pos);
        }
    }

    /**
     * Saved, BLOCK-BY-BLOCK contagion rooted in infected live/dead flowers.
     * Each 20-tick step moves through actual connected vanilla chorus_plant
     * blocks, then can infect attached flowers. Does not alter block IDs,
     * textures, collision or healthy unattached trees.
     */
    public void spreadAcrossChorus(ServerLevel level) {
        reconcileAllGrowth(level);
        // A recently grown infected flower may temporarily exist only as a
        // living chorus_plant stem. Do not erase that source while it grows buds.
        if (infected.isEmpty() && growthOrigins.isEmpty()) {
            if (!infectedStems.isEmpty()) {
                infectedStems.clear();
                setDirty();
            }
            return;
        }
        HashSet<Long> nextStems = new HashSet<>();
        HashSet<Long> completedOrigins = new HashSet<>();
        HashSet<Long> newFlowers = new HashSet<>();
        int seedBudget = 256;
        for (Long flowerKey : new ArrayList<>(infected)) {
            if (--seedBudget < 0) break;
            BlockPos flower = BlockPos.of(flowerKey);
            if (!level.hasChunkAt(flower) || !level.getBlockState(flower).is(Blocks.CHORUS_FLOWER))
                continue;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos adjacent = flower.relative(dir);
                if (level.hasChunkAt(adjacent)
                    && level.getBlockState(adjacent).is(Blocks.CHORUS_PLANT))
                    nextStems.add(adjacent.asLong());
            }
        }

        int spreadBudget = 256;
        for (Long stemKey : new ArrayList<>(infectedStems)) {
            if (--spreadBudget < 0) break;
            BlockPos stem = BlockPos.of(stemKey);
            if (!level.hasChunkAt(stem) || !level.getBlockState(stem).is(Blocks.CHORUS_PLANT))
                continue;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos adjacent = stem.relative(dir);
                if (!level.hasChunkAt(adjacent)) continue;
                if (level.getBlockState(adjacent).is(Blocks.CHORUS_PLANT)) {
                    nextStems.add(adjacent.asLong());
                } else if (level.getBlockState(adjacent).is(Blocks.CHORUS_FLOWER)
                    && !safe.contains(adjacent.asLong())
                    && (growthOrigins.contains(stemKey) || level.getRandom().nextInt(8) == 0)) {
                    // Direct children of a former infected flower inherit the
                    // infection immediately. Other buds are infected gradually.
                    newFlowers.add(adjacent.asLong());
                    if (growthOrigins.contains(stemKey)) completedOrigins.add(stemKey);
                }
            }
        }

        boolean changed = infectedStems.addAll(nextStems) | infected.addAll(newFlowers);
        changed |= growthOrigins.removeAll(completedOrigins);
        // A growing origin can be removed naturally if the infected block is
        // broken; do not leave a phantom source in saved world data.
        changed |= growthOrigins.removeIf(id -> {
            BlockPos pos = BlockPos.of(id);
            return level.hasChunkAt(pos) && !level.getBlockState(pos).is(Blocks.CHORUS_PLANT);
        });
        if (changed) setDirty();
        // Cleanup happens only in loaded components; never load chunks to spread.
        if (level.getGameTime() % 200L == 0L) pruneDisconnectedStems(level);
    }

    private void pruneDisconnectedStems(ServerLevel level) {
        HashSet<Long> reachable = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        for (Long seedKey : growthOrigins) {
            BlockPos seed = BlockPos.of(seedKey);
            if (level.hasChunkAt(seed)
                && level.getBlockState(seed).is(Blocks.CHORUS_PLANT)
                && reachable.add(seedKey)) queue.add(seed);
        }
        for (Long flowerKey : infected) {
            BlockPos flower = BlockPos.of(flowerKey);
            if (!level.hasChunkAt(flower)) continue;
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos stem = flower.relative(direction);
                if (infectedStems.contains(stem.asLong())
                    && level.hasChunkAt(stem) && reachable.add(stem.asLong()))
                    queue.add(stem);
            }
        }
        int budget = 8192;
        while (!queue.isEmpty() && --budget >= 0) {
            BlockPos at = queue.removeFirst();
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = at.relative(direction);
                if (infectedStems.contains(next.asLong())
                    && level.hasChunkAt(next) && reachable.add(next.asLong()))
                    queue.addLast(next);
            }
        }
        // Keep unloaded-stem records; they may still be connected in an
        // unloaded area and deleting them here would corrupt saved spread.
        boolean changed = infectedStems.removeIf(id -> {
            BlockPos pos = BlockPos.of(id);
            return level.hasChunkAt(pos) && !reachable.contains(id);
        });
        if (changed) setDirty();
    }

    /** Copy ids so event handlers may prune invalid flowers during iteration. */
    public java.util.List<Long> infectedIds() {
        return new java.util.ArrayList<>(infected);
    }

    public boolean mark(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            return false;
        }
        long key = pos.asLong();
        boolean changed = safe.remove(key) | infected.add(key);
        if (changed) setDirty();
        return true;
    }

    public void clear(BlockPos pos) {
        long key = pos.asLong();
        boolean changed = infected.remove(key) | safe.add(key);
        // A player who explicitly clears the only remaining source should
        // not leave self-reinfecting growth points behind.
        if (infected.isEmpty()) changed |= growthOrigins.removeIf(id -> id == key);
        if (changed) setDirty();
    }

    /**
     * Kill the mimic attached to this flower, not the whole chorus plant.
     * Its old position is temporarily exempt from seed-based reinfection:
     * the same mimic cannot immediately revive at the same coordinates.
     * An operator can still /livingchorus mark a new flower here explicitly.
     */
    public void defeat(BlockPos pos) {
        long key = pos.asLong();
        boolean changed = infected.remove(key) | safe.add(key);
        if (changed) setDirty();
    }

    /** Called only when the flower truly disappeared, not when it grew. */
    public void forget(BlockPos pos) {
        if (infected.remove(pos.asLong())) setDirty();
    }

    /**
     * Deterministic sampling is crucial: a flower that was not infected must
     * not get a fresh lottery ticket every time a player walks past.
     */
    public void discover(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        if (!level.getBlockState(pos).is(Blocks.CHORUS_FLOWER)) {
            reconcileFlower(level, pos);
            return;
        }
        if (infected.contains(key) || safe.contains(key)) return;

        long hash = mix64(level.getSeed() ^ key ^ 0x4D494D494343484FL);
        if ((hash & 0xFFFFL) < INFECTION_THRESHOLD && infected.add(key)) {
            setDirty();
        }
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
