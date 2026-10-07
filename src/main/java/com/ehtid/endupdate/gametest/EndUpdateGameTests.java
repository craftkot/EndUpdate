package com.ehtid.endupdate.gametest;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.blockentity.EndTrialCoreBlockEntity;
import com.ehtid.endupdate.registry.ModBlocks;
import com.ehtid.endupdate.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(EndUpdateMod.MODID)
@PrefixGameTestTemplate(false)
public final class EndUpdateGameTests {
    private static final String TRIAL_MOB = "EndUpdateTrialMob";
    private static final String TRIAL_X = "EndUpdateTrialX";
    private static final String TRIAL_Y = "EndUpdateTrialY";
    private static final String TRIAL_Z = "EndUpdateTrialZ";

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

    @GameTest(template = "empty", timeoutTicks = 320)
    public static void trialCoreRunsThreeWavesAndRewards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corePos = helper.absolutePos(BlockPos.ZERO).offset(0, 2, 0);

        // Wide floor for every randomized spawn point and for the mock player.
        for (int x = -12; x <= 24; x++) {
            for (int z = -12; z <= 12; z++) {
                level.setBlock(corePos.offset(x, -1, z), Blocks.END_STONE.defaultBlockState(), 3);
                level.setBlock(corePos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(corePos.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(corePos.offset(x, 2, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        level.setBlock(corePos, ModBlocks.END_TRIAL_CORE.get().defaultBlockState(), 3);
        helper.assertTrue(level.getBlockEntity(corePos) instanceof EndTrialCoreBlockEntity,
                "Trial Core block entity was not created");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setInvulnerable(true);
        // Keep the player inside the 24-block activation radius but far enough that mobs do not
        // naturally reach the fake player before each wave assertion.
        player.setPos(corePos.getX() + 20.5D, corePos.getY() + 4.0D, corePos.getZ() + 0.5D);

        helper.runAtTickTime(65L, () -> {
            List<Mob> mobs = trialMobs(level, corePos);
            helper.assertTrue(mobs.size() == 4,
                    "Wave 1 should contain 4 trial mobs, found " + mobs.size());
            discard(mobs);
        });

        helper.runAtTickTime(125L, () -> {
            List<Mob> mobs = trialMobs(level, corePos);
            helper.assertTrue(mobs.size() == 6,
                    "Wave 2 should contain 6 trial mobs, found " + mobs.size());
            discard(mobs);
        });

        helper.runAtTickTime(190L, () -> {
            List<Mob> mobs = trialMobs(level, corePos);
            helper.assertTrue(mobs.size() == 8,
                    "Wave 3 should contain 8 trial mobs, found " + mobs.size());
            discard(mobs);
        });

        helper.runAtTickTime(250L, () -> {
            helper.assertTrue(level.getBlockState(corePos.above()).is(Blocks.CHEST),
                    "Trial Core did not create the reward chest after wave 3");
            helper.assertTrue(trialMobs(level, corePos).isEmpty(),
                    "Trial mobs remained after completion");
            helper.succeed();
        });
    }

    private static List<Mob> trialMobs(ServerLevel level, BlockPos corePos) {
        AABB box = new AABB(corePos).inflate(32.0D, 10.0D, 32.0D);
        return level.getEntitiesOfClass(Mob.class, box, mob -> {
            var data = mob.getPersistentData();
            return data.getBoolean(TRIAL_MOB)
                    && data.getInt(TRIAL_X) == corePos.getX()
                    && data.getInt(TRIAL_Y) == corePos.getY()
                    && data.getInt(TRIAL_Z) == corePos.getZ();
        });
    }

    private static void discard(List<Mob> mobs) {
        for (Mob mob : mobs) {
            mob.discard();
        }
    }

    private EndUpdateGameTests() {}
}
