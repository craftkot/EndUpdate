package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EndSkeleton extends Skeleton {
    private int blinkCooldown = 35;

    public EndSkeleton(EntityType<? extends Skeleton> type, Level level) {
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
            if (TeleportUtil.chorusTeleportNear(this, target, 9.0D, 4.0D, 14)) {
                blinkCooldown = 45 + getRandom().nextInt(36);
            }
        } else if (getRandom().nextInt(5) == 0 && TeleportUtil.chorusTeleport(this, 6.0D, 3.0D, 8)) {
            blinkCooldown = 90 + getRandom().nextInt(41);
        }
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrowStack, float distanceFactor) {
        EnderArrowEntity arrow = new EnderArrowEntity(level(), this);
        arrow.setBaseDamage(2.0D + distanceFactor * 0.5D);
        return arrow;
    }
}
