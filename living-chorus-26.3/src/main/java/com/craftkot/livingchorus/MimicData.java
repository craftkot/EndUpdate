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
            Codec.LONG.listOf().fieldOf("safe").forGetter(data -> new ArrayList<>(data.safe))
        ).apply(instance, MimicData::new))
    );

    private static final int INFECTION_THRESHOLD = 3277; // approx. 5% of eligible flowers
    private final Set<Long> infected;
    private final Set<Long> safe;

    public MimicData() {
        this(List.of(), List.of());
    }

    private MimicData(List<Long> infected, List<Long> safe) {
        this.infected = new HashSet<>(infected);
        this.safe = new HashSet<>(safe);
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
