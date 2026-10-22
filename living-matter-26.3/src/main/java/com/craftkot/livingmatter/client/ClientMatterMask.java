package com.craftkot.livingmatter.client;

import com.craftkot.livingmatter.LivingMatter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Render-only concealment for an awake mimic. There is NO blockstate
 * mutation, no invisible replacement block, no world collision change.
 * Only a leaf branch which provably has no junction is masked. A junction
 * cannot belong to one tip without hiding neighboring healthy branches.
 */
@EventBusSubscriber(modid = LivingMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientMatterMask {
    private static final int MAX_TERMINAL_STEM_BLOCKS = 4;
    private static final Map<Long, Set<Long>> BY_TIP = new HashMap<>();
    private static final Map<Long, Set<Long>> BY_WALK = new HashMap<>();

    // Chunk meshing runs asynchronously; readers need an immutable snapshot.
    private static volatile Set<Long> hidden = Set.of();

    private ClientMatterMask() {}

    /** Runs on the MOD bus during model reload, not the render thread. */
    @SubscribeEvent
    public static void wrapVanillaModels(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models =
            event.getBakingResult().blockStateModels();
        // The same invisible-awake-block technique as Living Chorus.
        // Only a handful of positions are hidden at a time, but any
        // original vanilla block model may be the material of a walker.
        models.replaceAll((state, model) -> new MaskableModel(model));
    }

    /** Mask ONLY tip-owned blocks in the real branch, never the shared root. */
    public static void start(ClientLevel level, BlockPos tip, List<BlockPos> rootToFlower) {
        Set<Long> part = new HashSet<>();
        part.add(tip.asLong());
        for (int i=1;i<rootToFlower.size();i++) {
            BlockPos piece = rootToFlower.get(i);
            BlockState block = level.getBlockState(piece);
            if (!block.isAir() && !block.hasBlockEntity()) part.add(piece.asLong());
        }
        BY_TIP.put(tip.asLong(), Set.copyOf(part));
        rebuild(level);
    }

    public static void stop(ClientLevel level, BlockPos tip) {
        if (BY_TIP.remove(tip.asLong()) != null) rebuild(level);
    }

    public static void reset(ClientLevel level) {
        Set<Long> was = hidden;
        BY_TIP.clear();
        BY_WALK.clear();
        hidden = Set.of();
        if (level != null) dirty(level, was);
    }

    public static boolean isHidden(BlockPos pos) {
        return hidden.contains(pos.asLong());
    }

    public static void startWalk(ClientLevel level, BlockPos marker,
                                 java.util.List<BlockPos> from, int dx, int dz) {
        Set<Long> parts = new HashSet<>();
        for (BlockPos p : from) {
            parts.add(p.asLong());
            parts.add(p.offset(dx, 0, dz).asLong());
        }
        BY_WALK.put(marker.asLong(), Set.copyOf(parts));
        rebuild(level);
    }

    public static void stopWalk(ClientLevel level, BlockPos marker) {
        if (BY_WALK.remove(marker.asLong()) != null) rebuild(level);
    }

    private static void rebuild(ClientLevel level) {
        Set<Long> before = hidden;
        Set<Long> union = new HashSet<>();
        for (Set<Long> mask : BY_TIP.values()) union.addAll(mask);
        for (Set<Long> mask : BY_WALK.values()) union.addAll(mask);
        hidden = Set.copyOf(union);
        Set<Long> changed = new HashSet<>(before);
        changed.addAll(union);
        dirty(level, changed);
    }

    private static void dirty(ClientLevel level, Set<Long> changed) {
        // Force a section rebuild: its baked mesh no longer contains old quads.
        // Does not modify the world or notify the server.
        Set<Long> sections = new HashSet<>();
        for (long id : changed) {
            BlockPos pos = BlockPos.of(id);
            long key = BlockPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
            if (sections.add(key)) {
                level.setSectionDirtyWithNeighbors(pos.getX() >> 4,
                    pos.getY() >> 4, pos.getZ() >> 4);
            }
        }
    }

    private static final class MaskableModel extends DelegateBlockStateModel {
        private MaskableModel(BlockStateModel delegate) {
            super(delegate);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos,
                                 BlockState state, RandomSource random,
                                 List<BlockStateModelPart> output) {
            if (!isHidden(pos)) {
                super.collectParts(level, pos, state, random, output);
            }
        }

        // Use NeoForge's default non-cached geometry behavior so a section
        // rebuild always reevaluates the per-position awake mask.
    }
}
