package com.ehtid.endupdate.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EndSkeleton extends Skeleton {
    public EndSkeleton(EntityType<? extends Skeleton> type, Level level) { super(type, level); }

    @Override
    protected AbstractArrow getArrow(ItemStack arrowStack, float distanceFactor) {
        EnderArrowEntity arrow = new EnderArrowEntity(level(), this);
        arrow.setBaseDamage(2.0D + distanceFactor * 0.5D);
        return arrow;
    }
}
