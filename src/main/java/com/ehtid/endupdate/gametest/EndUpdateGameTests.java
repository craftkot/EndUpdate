package com.ehtid.endupdate.gametest;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(EndUpdateMod.MODID)
@PrefixGameTestTemplate(false)
public final class EndUpdateGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teleportEffectMovesEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(BlockPos.ZERO).above();

        // Give chorus-style teleportation a deterministic safe landing area.
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                level.setBlock(center.offset(x, -1, z), Blocks.END_STONE.defaultBlockState(), 3);
                level.setBlock(center.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(center.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        LivingEntity target = EntityType.COW.create(level);
        helper.assertTrue(target != null, "Failed to create teleportation test target");
        if (target == null) return;

        target.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(target);
        Vec3 before = target.position();

        ModEffects.CHORUS_TELEPORT.get().applyInstantenousEffect(null, null, target, 0, 1.0D);

        helper.assertTrue(before.distanceToSqr(target.position()) > 0.25D,
                "Chorus teleport effect did not move the target");
        helper.succeed();
    }

    private EndUpdateGameTests() {}
}
