package com.craftkot.livingchorus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class LivingChorusBlock extends Block {
    private static final int ATTACK_INTERVAL = 20;
    private static final double ATTACK_RANGE = 2.25;

    public LivingChorusBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            level.scheduleTick(pos, this, ATTACK_INTERVAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AABB area = new AABB(pos).inflate(ATTACK_RANGE);
        for (Player player : level.getEntitiesOfClass(Player.class, area, p -> !p.isSpectator() && !p.isCreative())) {
            player.hurtServer(level, level.damageSources().cactus(), 3.0F);
        }
        if (level.getBlockState(pos).is(this)) {
            level.scheduleTick(pos, this, ATTACK_INTERVAL);
        }
    }
}
