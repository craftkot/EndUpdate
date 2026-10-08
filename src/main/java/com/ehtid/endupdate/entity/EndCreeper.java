package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

public class EndCreeper extends Creeper {
    private int blinkCooldown = 30;

    public EndCreeper(EntityType<? extends Creeper> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        if (blinkCooldown > 0) {
            blinkCooldown--;
            return;
        }

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            double radius = getSwellDir() > 0 ? 3.5D : 6.0D;
            if (TeleportUtil.chorusTeleportNear(this, target, radius, 3.0D, 14)) {
                blinkCooldown = getSwellDir() > 0 ? 35 : 55 + getRandom().nextInt(31);
            }
        } else if (getRandom().nextInt(5) == 0 && TeleportUtil.chorusTeleport(this, 5.0D, 3.0D, 8)) {
            blinkCooldown = 90 + getRandom().nextInt(41);
        }
    }
}
