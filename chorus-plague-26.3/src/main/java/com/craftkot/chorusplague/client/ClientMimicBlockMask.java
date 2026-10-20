package com.craftkot.chorusplague.client;

import com.craftkot.chorusplague.ChorusPlague;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
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
 * cannot belong to one flower without hiding neighboring healthy branches.
 */
@EventBusSubscriber(modid = ChorusPlague.MOD_ID, value = Dist.CLIENT)
public final class ClientMimicBlockMask {
    private static final int MAX_TERMINAL_STEM_BLOCKS = 4;
    private static final Map<Long, Set<Long>> BY_FLOWER = new HashMap<>();

    // Chunk meshing runs asynchronously; readers need an immutable snapshot.
    private static volatile Set<Long> hidden = Set.of();

    private ClientMimicBlockMask() {}

    /** Runs on the MOD bus during model reload, not the render thread. */
    @SubscribeEvent
    public static void wrapVanillaModels(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models =
            event.getBakingResult().blockStateModels();
        for (BlockState state : Blocks.CHORUS_FLOWER.getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(state, (ignored, model) -> new MaskableModel(model));
        }
        for (BlockState state : Blocks.CHORUS_PLANT.getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(state, (ignored, model) -> new MaskableModel(model));
        }
    }

    /** Mask ONLY flower-owned blocks in the real branch, never the shared root. */
    public static void start(ClientLevel level, BlockPos flower, List<BlockPos> rootToFlower) {
        Set<Long> part = new HashSet<>();
        part.add(flower.asLong());
        for (int i=1;i<rootToFlower.size();i++) {
            BlockPos piece = rootToFlower.get(i);
            BlockState block = level.getBlockState(piece);
            if (block.is(Blocks.CHORUS_PLANT) || block.is(Blocks.CHORUS_FLOWER))
                part.add(piece.asLong());
        }
        BY_FLOWER.put(flower.asLong(), Set.copyOf(part));
        rebuild(level);
    }

    public static void stop(ClientLevel level, BlockPos flower) {
        if (BY_FLOWER.remove(flower.asLong()) != null) rebuild(level);
    }

    public static void reset(ClientLevel level) {
        Set<Long> was = hidden;
        BY_FLOWER.clear();
        hidden = Set.of();
        if (level != null) dirty(level, was);
    }

    public static boolean isHidden(BlockPos pos) {
        return hidden.contains(pos.asLong());
    }

    private static BlockPos onlyAdjacentPlant(ClientLevel level, BlockPos tip) {
        BlockPos candidate = null;
        for (Direction d : Direction.values()) {
            BlockPos n = tip.relative(d);
            if (level.getBlockState(n).is(Blocks.CHORUS_PLANT)) {
                if (candidate != null) return null;
                candidate = n;
            }
        }
        return candidate;
    }

    private static List<BlockPos> adjacentChorus(ClientLevel level, BlockPos pos) {
        List<BlockPos> found = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState block = level.getBlockState(n);
            if (block.is(Blocks.CHORUS_FLOWER) || block.is(Blocks.CHORUS_PLANT)) {
                found.add(n);
            }
        }
        return found;
    }

    private static void rebuild(ClientLevel level) {
        Set<Long> before = hidden;
        Set<Long> union = new HashSet<>();
        for (Set<Long> mask : BY_FLOWER.values()) union.addAll(mask);
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
