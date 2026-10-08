package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

public class EndZombie extends Zombie {
    private int blinkCooldown = 30;

    public EndZombie(EntityType<? extends Zombie> type, Level level) {
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
            if (TeleportUtil.chorusTeleportNear(this, target, 5.0D, 3.0D, 12)) {
                blinkCooldown = 35 + getRandom().nextInt(31);
            }
        } else if (getRandom().nextInt(5) == 0 && TeleportUtil.chorusTeleport(this, 5.0D, 3.0D, 8)) {
            blinkCooldown = 80 + getRandom().nextInt(41);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() != null) {
            if (TeleportUtil.chorusTeleport(this, 6.0D, 4.0D, 12)) {
                blinkCooldown = 18;
            }
        }
        return hurt;
    }
}
