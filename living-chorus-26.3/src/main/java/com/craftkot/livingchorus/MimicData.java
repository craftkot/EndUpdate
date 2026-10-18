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
                .forGetter(data -> new ArrayList<>(data.infectedStems))
        ).apply(instance, MimicData::new))
    );

    private static final int INFECTION_THRESHOLD = 3277; // approx. 5% of eligible flowers
    private final Set<Long> infected;
    private final Set<Long> safe;
    private final Set<Long> infectedStems;

    public MimicData() {
        this(List.of(), List.of(), List.of());
    }

    private MimicData(List<Long> infected, List<Long> safe, List<Long> stems) {
        this.infected = new HashSet<>(infected);
        this.safe = new HashSet<>(safe);
        this.infectedStems = new HashSet<>(stems);
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

    /**
     * Saved, BLOCK-BY-BLOCK contagion rooted in infected live/dead flowers.
     * Each 20-tick step moves through actual connected vanilla chorus_plant
     * blocks, then can infect attached flowers. Does not alter block IDs,
     * textures, collision or healthy unattached trees.
     */
    public void spreadAcrossChorus(ServerLevel level) {
        if (infected.isEmpty()) {
            if (!infectedStems.isEmpty()) {
                infectedStems.clear();
                setDirty();
            }
            return;
        }
        HashSet<Long> nextStems = new HashSet<>();
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
                    && !safe.contains(adjacent.asLong()) && !infected.contains(adjacent.asLong())
                    && level.getRandom().nextInt(8) == 0) {
                    // Spread to ANOTHER flower only along real infected chorus blocks.
                    newFlowers.add(adjacent.asLong());
                }
            }
        }

        boolean changed = infectedStems.addAll(nextStems) | infected.addAll(newFlowers);
        if (changed) setDirty();
        // Cleanup happens only in loaded components; never load chunks to spread.
        if (level.getGameTime() % 200L == 0L) pruneDisconnectedStems(level);
    }

    private void pruneDisconnectedStems(ServerLevel level) {
        HashSet<Long> reachable = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
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

    /** Drop the marker when the flower is removed; a new flower may be infected later. */
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
            if (infected.remove(key)) setDirty();
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
