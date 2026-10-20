package com.craftkot.livingmatter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A purely visual temporary reveal. Never has collision, an item or loot.
 * Its scheduled block tick advances the three keyframe models and removes it.
 * Scheduled ticks also survive chunk save/reload.
 */
public final class MimicManifestBlock extends Block {
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 2);

    public MimicManifestBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PHASE, 0));
    }

    /** The visible tentacles and jaws do not block mining, movement or aiming. */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
        BlockState state, net.minecraft.world.level.BlockGetter level,
        BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) level.scheduleTick(pos, this, 7);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockState(pos).is(this)) return;
        int phase = state.getValue(PHASE);
        if (phase == 2) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(PHASE, phase + 1), 2);
            level.scheduleTick(pos, this, 7);
        }
    }
}
