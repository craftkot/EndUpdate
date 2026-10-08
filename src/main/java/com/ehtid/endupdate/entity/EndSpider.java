package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

public class EndSpider extends Spider {
    private int blinkCooldown = 20;

    public EndSpider(EntityType<? extends Spider> type, Level level) {
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
            if (TeleportUtil.chorusTeleportNear(this, target, 4.5D, 3.0D, 12)) {
                blinkCooldown = 28 + getRandom().nextInt(25);
            }
        } else if (getRandom().nextInt(4) == 0 && TeleportUtil.chorusTeleport(this, 5.0D, 3.0D, 8)) {
            blinkCooldown = 70 + getRandom().nextInt(31);
        }
    }
}
