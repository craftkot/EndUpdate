package com.craftkot.livingmatter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Write-ahead recovery journal for real vanilla leaves temporarily moving.
 * After a crash/restart, orphaned moves revert when their chunks load.
 */
public final class LeafRecoveryData extends SavedData {
    public record Change(long origin, long donor, long extension,
                         BlockState original, BlockState moving) {
        public static final Codec<Change> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("origin").forGetter(Change::origin),
            Codec.LONG.fieldOf("donor").forGetter(Change::donor),
            Codec.LONG.fieldOf("extension").forGetter(Change::extension),
            BlockState.CODEC.fieldOf("original").forGetter(Change::original),
            BlockState.CODEC.fieldOf("moving").forGetter(Change::moving)
        ).apply(i, Change::new));
    }

    public static final SavedDataType<LeafRecoveryData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(LivingMatter.MOD_ID, "leaf_recovery"),
        LeafRecoveryData::new,
        RecordCodecBuilder.create(i -> i.group(
            Change.CODEC.listOf().fieldOf("changes")
                .forGetter(data -> new ArrayList<>(data.changes))
        ).apply(i, LeafRecoveryData::new))
    );

    private final List<Change> changes;
    private LeafRecoveryData(List<Change> changes) {
        this.changes = new ArrayList<>(changes);
    }
    public LeafRecoveryData() { this(List.of()); }

    public static LeafRecoveryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void beforeMove(BlockPos root, BlockPos donor, BlockPos to, BlockState original, BlockState visual) {
        changes.add(new Change(root.asLong(), donor.asLong(), to.asLong(), original, visual));
        setDirty();
    }

    public void undoPending(BlockPos root, BlockPos donor, BlockPos to) {
        if (changes.removeIf(c -> c.origin == root.asLong()
            && c.donor == donor.asLong() && c.extension == to.asLong())) setDirty();
    }

    public void finish(BlockPos root) {
        if (changes.removeIf(c -> c.origin == root.asLong())) setDirty();
    }

    public void recoverOrphans(ServerLevel level, Set<Long> activeRoots) {
        if (changes.isEmpty()) return;
        Map<Long, List<Change>> byTree = new LinkedHashMap<>();
        for (Change change : changes) {
            if (!activeRoots.contains(change.origin))
                byTree.computeIfAbsent(change.origin, unused -> new ArrayList<>()).add(change);
        }
        for (var entry : byTree.entrySet()) {
            List<Change> orphan = entry.getValue();
            boolean loaded = true;
            for (Change change : orphan) {
                if (!level.hasChunkAt(BlockPos.of(change.donor))
                    || !level.hasChunkAt(BlockPos.of(change.extension))) {
                    loaded = false;
                    break;
                }
            }
            if (!loaded) continue;
            for (int i = orphan.size() - 1; i >= 0; i--) {
                Change change = orphan.get(i);
                BlockPos donor = BlockPos.of(change.donor);
                BlockPos to = BlockPos.of(change.extension);
                if (level.getBlockState(to).getBlock() == change.moving.getBlock())
                    level.setBlock(to, Blocks.AIR.defaultBlockState(), 3);
                if (level.getBlockState(donor).isAir())
                    level.setBlock(donor, change.original, 3);
            }
            if (changes.removeAll(orphan)) setDirty();
        }
    }
}
