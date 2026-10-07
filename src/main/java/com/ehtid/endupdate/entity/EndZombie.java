package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

public class EndZombie extends Zombie {
    private int blinkCooldown;

    public EndZombie(EntityType<? extends Zombie> type, Level level) { super(type, level); }

    @Override
    public void tick() {
        super.tick();
        if (blinkCooldown > 0) blinkCooldown--;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && blinkCooldown == 0 && source.getEntity() != null) {
            TeleportUtil.chorusTeleport(this, 6.0D, 4.0D, 12);
            blinkCooldown = 12;
        }
        return hurt;
    }
}
