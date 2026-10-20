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
        if (state.getBlock() instanceof MimicManifestBlock) return false;
        if (state.isAir() || !state.getFluidState().isEmpty()
            || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return false;
        // Avoid gameplay-critical, moving or unbreakable technical blocks.
        if (state.is(Blocks.BEDROCK) || state.is(Blocks.END_PORTAL)
            || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_GATEWAY)
            || state.is(Blocks.MOVING_PISTON) || state.is(Blocks.PISTON_HEAD)) return false;
        return true;
    }

    /**
     * A predator that successfully feeds can infect one neighbouring block.
     * The infection spreads within compatible materials (tree logs -> leaves,
     * stone -> nearby stone/ore); each local colony has a hard density cap.
     * No block states change, and only loaded chunks may be inspected.
     */
    public boolean spreadAfterHunt(ServerLevel level, BlockPos origin, MimicKind kind, boolean forced) {
        if (!isInfected(origin) || !level.hasChunkAt(origin)) return false;
        if (!forced && level.getRandom().nextInt(2) != 0) return false;
        int local = 0;
        for (long id : infected) {
            BlockPos other = BlockPos.of(id);
            long dx = (long) other.getX() - origin.getX();
            long dy = (long) other.getY() - origin.getY();
            long dz = (long) other.getZ() - origin.getZ();
            if (dx * dx + dy * dy + dz * dz <= 64L) local++;
        }
        if (local >= 12) return false;
        net.minecraft.core.Direction[] directions = net.minecraft.core.Direction.values();
        int offset = level.getRandom().nextInt(directions.length);
        for (int i = 0; i < directions.length; i++) {
            BlockPos next = origin.relative(directions[(i + offset) % directions.length]);
            if (!eligible(level, next) || isInfected(next) || cleared.contains(next.asLong())) continue;
            MimicKind target = MimicKind.of(level.getBlockState(next));
            boolean compatible = kind == target
                || (kind == MimicKind.WOOD && target == MimicKind.LEAF)
                || (kind == MimicKind.LEAF && target == MimicKind.WOOD)
                || (kind == MimicKind.STONE && target == MimicKind.ORE)
                || (kind == MimicKind.SPORE && target == MimicKind.LEAF);
            if (!compatible) continue;
            if (infected.add(next.asLong())) {
                setDirty();
                return true;
            }
        }
        return false;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
