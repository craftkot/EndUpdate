package com.craftkot.livingmatter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Infection is a hidden SERVER-side marker. Vanilla blocks are never replaced
 * by dormant mimics; all existing block states, loot and block entities remain.
 */
public final class MimicData extends SavedData {
    public static final SavedDataType<MimicData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(LivingMatter.MOD_ID, "infection"),
        MimicData::new,
        RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.listOf().fieldOf("infected").forGetter(d -> new ArrayList<>(d.infected)),
            Codec.LONG.listOf().optionalFieldOf("cleared", List.of())
                .forGetter(d -> new ArrayList<>(d.cleared))
        ).apply(instance, MimicData::new))
    );
    private final Set<Long> infected;
    private final Set<Long> cleared;

    public MimicData() { this(List.of(), List.of()); }
    private MimicData(List<Long> infected, List<Long> cleared) {
        this.infected = new HashSet<>(infected);
        this.cleared = new HashSet<>(cleared);
    }

    public static MimicData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isInfected(BlockPos pos) { return infected.contains(pos.asLong()); }
    public int count() { return infected.size(); }

    public boolean mark(ServerLevel level, BlockPos pos) {
        if (!eligible(level, pos)) return false;
        boolean changed = cleared.remove(pos.asLong()) | infected.add(pos.asLong());
        if (changed) setDirty();
        return true;
    }

    public void cure(BlockPos pos) {
        boolean changed = infected.remove(pos.asLong()) | cleared.add(pos.asLong());
        if (changed) setDirty();
    }

    /** Deterministic world generation by block coordinate, no reroll per tick. */
    public void discover(ServerLevel level, BlockPos pos) {
        long key = pos.asLong();
        if (infected.contains(key) || cleared.contains(key) || !eligible(level, pos)) return;
        long hash = mix64(level.getSeed() ^ key ^ 0x4C4956494E474D41L);
        // Roughly 0.09% per eligible block position: rare but widespread.
        if ((hash & 0xFFFFL) < 60L && infected.add(key)) setDirty();
    }

    public static boolean eligible(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()
            || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        // Avoid gameplay-critical, moving or unbreakable technical blocks.
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.END_PORTAL)
            || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_GATEWAY)
            || state.is(Blocks.MOVING_PISTON) || state.is(Blocks.PISTON_HEAD)) return false;
        return true;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
