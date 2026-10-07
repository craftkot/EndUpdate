package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

public class EndCreeper extends Creeper {
    private int blinkCooldown;

    public EndCreeper(EntityType<? extends Creeper> type, Level level) { super(type, level); }

    @Override
    public void tick() {
        super.tick();
        if (blinkCooldown > 0) blinkCooldown--;
        if (!level().isClientSide && blinkCooldown == 0 && getSwellDir() > 0) {
            LivingEntity target = getTarget();
            if (target != null && distanceToSqr(target) > 9.0D) {
                if (TeleportUtil.chorusTeleportNear(this, target, 4.0D, 2.5D, 10)) blinkCooldown = 60;
            }
        }
    }
}
