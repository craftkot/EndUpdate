package com.craftkot.livingchorus.mixin;

import com.craftkot.livingchorus.MimicAwakening;
import com.craftkot.livingchorus.client.ClientMimicBlockMask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only the original blocks of an ACTIVE moving mimic stop colliding and being
 * selectable. Vanilla sleeping chorus retains its normal collision and outline.
 * Server state and client packet mask are independent and dimension-scoped.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ChorusMovingShapeMixin {
    @Unique
    private boolean livingchorus$isGhost(BlockGetter level, BlockPos pos) {
        BlockState self = (BlockState)(Object)this;
        if (!self.is(Blocks.CHORUS_FLOWER) && !self.is(Blocks.CHORUS_PLANT))
            return false;
        if (level instanceof ServerLevel server)
            return MimicAwakening.isMovingPart(server, pos);
        // This is only resolved in physical client JVMs; dedicated servers
        // never load the client mask or client-only classes.
        return FMLEnvironment.getDist() == Dist.CLIENT
            && ClientMimicBlockMask.isHidden(pos);
    }

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"), cancellable = true)
    private void livingchorus$noMovingCollision(BlockGetter level, BlockPos pos,
                                                CollisionContext context,
                                                CallbackInfoReturnable<VoxelShape> result) {
        if (livingchorus$isGhost(level, pos)) result.setReturnValue(Shapes.empty());
    }

    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"), cancellable = true)
    private void livingchorus$noMovingSelection(BlockGetter level, BlockPos pos,
                                                CollisionContext context,
                                                CallbackInfoReturnable<VoxelShape> result) {
        if (livingchorus$isGhost(level, pos)) result.setReturnValue(Shapes.empty());
    }
}
