package com.ehtid.endupdate.util;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class TeleportUtil {
    public static boolean chorusTeleport(LivingEntity entity, double horizontalRange, double verticalRange, int attempts) {
        Level level = entity.level();
        if (level.isClientSide || !entity.isAlive()) return false;

        double ox = entity.getX();
        double oy = entity.getY();
        double oz = entity.getZ();

        for (int i = 0; i < attempts; i++) {
            double x = ox + (entity.getRandom().nextDouble() - 0.5D) * 2.0D * horizontalRange;
            double y = clampY(level, oy + (entity.getRandom().nextDouble() - 0.5D) * 2.0D * verticalRange);
            double z = oz + (entity.getRandom().nextDouble() - 0.5D) * 2.0D * horizontalRange;
            if (tryTeleport(entity, x, y, z, ox, oy, oz)) return true;
        }
        return false;
    }

    public static boolean chorusTeleportNear(LivingEntity entity, LivingEntity target,
                                              double targetRadius, double verticalRange, int attempts) {
        Level level = entity.level();
        if (level.isClientSide || !entity.isAlive() || !target.isAlive()) return false;

        double ox = entity.getX();
        double oy = entity.getY();
        double oz = entity.getZ();

        for (int i = 0; i < attempts; i++) {
            double angle = entity.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 1.8D + entity.getRandom().nextDouble() * Math.max(0.1D, targetRadius - 1.8D);
            double x = target.getX() + Math.cos(angle) * radius;
            double y = clampY(level, target.getY() + (entity.getRandom().nextDouble() - 0.5D) * 2.0D * verticalRange);
            double z = target.getZ() + Math.sin(angle) * radius;
            if (tryTeleport(entity, x, y, z, ox, oy, oz)) return true;
        }
        return false;
    }

    private static double clampY(Level level, double y) {
        return Mth.clamp(y, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 1);
    }

    private static boolean tryTeleport(LivingEntity entity, double x, double y, double z,
                                       double oldX, double oldY, double oldZ) {
        if (!entity.randomTeleport(x, y, z, true)) return false;
        Level level = entity.level();
        SoundSource source = entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        level.playSound(null, oldX, oldY, oldZ, SoundEvents.CHORUS_FRUIT_TELEPORT, source, 1.0F, 1.0F);
        entity.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
        entity.resetFallDistance();
        return true;
    }

    private TeleportUtil() {}
}
