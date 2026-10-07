package com.ehtid.endupdate.blockentity;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.registry.ModBlockEntities;
import com.ehtid.endupdate.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class EndTrialCoreBlockEntity extends BlockEntity {
    private static final int RESET_TICKS = 20 * 60 * 10;
    private static final int ABANDON_RESET_TICKS = 20 * 30;
    private static final double TRIAL_RADIUS = 24.0D;
    private static final String TRIAL_MOB = "EndUpdateTrialMob";
    private static final String TRIAL_X = "EndUpdateTrialX";
    private static final String TRIAL_Y = "EndUpdateTrialY";
    private static final String TRIAL_Z = "EndUpdateTrialZ";

    private int phase; // 0 idle, 1 active, 2 cooldown
    private int wave;
    private int delay;
    private int cooldown;
    private int noPlayerTicks;

    public EndTrialCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.END_TRIAL_CORE.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level rawLevel, BlockPos pos, BlockState state, EndTrialCoreBlockEntity core) {
        if (!(rawLevel instanceof ServerLevel level)) return;

        if (core.phase == 2) {
            if (core.cooldown > 0) core.cooldown--;
            if (core.cooldown <= 0) {
                core.phase = 0;
                core.wave = 0;
                core.noPlayerTicks = 0;
                core.setChanged();
            }
            return;
        }

        AABB arena = new AABB(pos).inflate(TRIAL_RADIUS, 7.0D, TRIAL_RADIUS);

        // Idle cores are permanent worldgen block entities, so do not scan a large mob box every tick.
        // A 10-tick activation poll is responsive to players and much cheaper when many dungeons are loaded.
        if (core.phase == 0) {
            if (level.getGameTime() % 10L != 0L) return;
            List<Player> enteringPlayers = level.getEntitiesOfClass(Player.class, arena, p -> !p.isSpectator() && p.isAlive());
            if (enteringPlayers.isEmpty()) return;

            core.phase = 1;
            core.wave = 1;
            core.delay = 30;
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.65F);
            level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                    50, 1.5D, 0.7D, 1.5D, 0.15D);
            core.setChanged();
            return;
        }

        AABB trackingArea = arena.inflate(8.0D, 8.0D, 8.0D);
        List<Mob> trialMobs = level.getEntitiesOfClass(Mob.class, trackingArea, m -> core.isTrialMobFor(m, pos));

        // Do this even when every player has left. Otherwise a teleporting trial mob can escape the
        // tracking box and become a permanent stray entity while the core waits forever.
        core.keepTrialMobsInside(level, pos, arena, trialMobs);

        List<Player> players = level.getEntitiesOfClass(Player.class, arena, p -> !p.isSpectator() && p.isAlive());
        if (players.isEmpty()) {
            core.noPlayerTicks++;
            if (core.noPlayerTicks >= ABANDON_RESET_TICKS) {
                core.resetAbandonedTrial(trialMobs);
            }
            return;
        }
        core.noPlayerTicks = 0;

        if (core.delay > 0) {
            core.delay--;
            return;
        }

        if (!trialMobs.isEmpty()) return;

        if (core.wave <= 3) {
            core.spawnWave(level, pos, core.wave, players.size());
            level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.1F, 0.8F + core.wave * 0.1F);
            level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    80, 4.0D, 1.0D, 4.0D, 0.3D);
            core.wave++;
            core.delay = 50;
            core.setChanged();
        } else {
            core.complete(level, pos);
        }
    }

    private void spawnWave(ServerLevel level, BlockPos center, int waveNumber, int playerCount) {
        // Solo stays close to the original balance; extra players add pressure without growing without bound.
        int extraForParty = Math.min(4, Math.max(0, playerCount - 1) * 2);
        int count = 2 + waveNumber * 2 + extraForParty;
        for (int i = 0; i < count; i++) {
            EntityType<? extends Mob> type;
            if (waveNumber == 1) {
                type = (i & 1) == 0 ? ModEntities.END_ZOMBIE.get() : ModEntities.END_SPIDER.get();
            } else if (waveNumber == 2) {
                type = i % 3 == 0 ? ModEntities.END_CREEPER.get() : ModEntities.END_SKELETON.get();
            } else {
                type = switch ((i + level.random.nextInt(4)) & 3) {
                    case 0 -> ModEntities.END_ZOMBIE.get();
                    case 1 -> ModEntities.END_SKELETON.get();
                    case 2 -> ModEntities.END_CREEPER.get();
                    default -> ModEntities.END_SPIDER.get();
                };
            }

            Mob mob = type.create(level);
            if (mob == null) continue;
            double spawnX;
            double spawnZ;
            if (waveNumber == 1) {
                double angle = (Math.PI * 2.0D * i / count) + level.random.nextDouble() * 0.4D;
                double radius = 5.0D + level.random.nextDouble() * 3.0D;
                spawnX = center.getX() + 0.5D + Math.cos(angle) * radius;
                spawnZ = center.getZ() + 0.5D + Math.sin(angle) * radius;
            } else {
                // Later waves make the four side rooms matter: enemies enter from the chambers
                // instead of every wave appearing in the same circle around the core.
                int side = i & 3;
                int roomX = side == 0 ? 18 : side == 1 ? -18 : 0;
                int roomZ = side == 2 ? 18 : side == 3 ? -18 : 0;
                double spread = waveNumber == 2 ? 2.5D : 4.0D;
                spawnX = center.getX() + 0.5D + roomX + (level.random.nextDouble() - 0.5D) * spread;
                spawnZ = center.getZ() + 0.5D + roomZ + (level.random.nextDouble() - 0.5D) * spread;
            }
            BlockPos spawn = BlockPos.containing(spawnX, center.getY() + 1.0D, spawnZ);
            mob.moveTo(spawn, level.random.nextFloat() * 360.0F, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.SPAWNER, null, null);
            mob.setPersistenceRequired();
            mob.getPersistentData().putBoolean(TRIAL_MOB, true);
            mob.getPersistentData().putInt(TRIAL_X, center.getX());
            mob.getPersistentData().putInt(TRIAL_Y, center.getY());
            mob.getPersistentData().putInt(TRIAL_Z, center.getZ());
            level.addFreshEntity(mob);
        }
    }

    private void resetAbandonedTrial(List<Mob> trialMobs) {
        for (Mob mob : trialMobs) {
            if (mob.isAlive()) mob.discard();
        }
        phase = 0;
        wave = 0;
        delay = 0;
        noPlayerTicks = 0;
        setChanged();
    }

    private void complete(ServerLevel level, BlockPos center) {
        phase = 2;
        cooldown = RESET_TICKS;
        wave = 0;
        delay = 0;
        noPlayerTicks = 0;

        BlockPos rewardPos = center.above();
        if (level.getBlockState(rewardPos).isAir()) {
            level.setBlock(rewardPos, Blocks.CHEST.defaultBlockState(), 3);
        }
        if (level.getBlockEntity(rewardPos) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            chest.setLootTable(new ResourceLocation(EndUpdateMod.MODID, "chests/end_trial_reward"), level.random.nextLong());
            chest.setChanged();
        }
        level.playSound(null, center, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.PORTAL, center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D,
                140, 3.0D, 2.0D, 3.0D, 0.4D);
        setChanged();
    }

    private boolean isTrialMobFor(Mob mob, BlockPos center) {
        CompoundTag data = mob.getPersistentData();
        return data.getBoolean(TRIAL_MOB)
                && data.getInt(TRIAL_X) == center.getX()
                && data.getInt(TRIAL_Y) == center.getY()
                && data.getInt(TRIAL_Z) == center.getZ();
    }

    private void keepTrialMobsInside(ServerLevel level, BlockPos center, AABB arena, List<Mob> mobs) {
        for (Mob mob : mobs) {
            if (arena.contains(mob.position())) continue;
            double x = center.getX() + 0.5D + (level.random.nextDouble() - 0.5D) * 16.0D;
            double z = center.getZ() + 0.5D + (level.random.nextDouble() - 0.5D) * 16.0D;
            mob.teleportTo(x, center.getY() + 1.0D, z);
            mob.getNavigation().stop();
            mob.resetFallDistance();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Phase", phase);
        tag.putInt("Wave", wave);
        tag.putInt("Delay", delay);
        tag.putInt("Cooldown", cooldown);
        tag.putInt("NoPlayerTicks", noPlayerTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        phase = tag.getInt("Phase");
        wave = tag.getInt("Wave");
        delay = tag.getInt("Delay");
        cooldown = tag.getInt("Cooldown");
        noPlayerTicks = tag.getInt("NoPlayerTicks");
    }
}
