package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

public class EndSpider extends Spider {
    private int blinkCooldown;

    public EndSpider(EntityType<? extends Spider> type, Level level) { super(type, level); }

    @Override
    public void tick() {
        super.tick();
        if (blinkCooldown > 0) blinkCooldown--;
        if (!level().isClientSide && blinkCooldown == 0) {
            LivingEntity target = getTarget();
            if (target != null && distanceToSqr(target) > 36.0D && getRandom().nextInt(18) == 0) {
                if (TeleportUtil.chorusTeleport(this, 5.0D, 3.0D, 10)) blinkCooldown = 80;
            }
        }
    }
}
