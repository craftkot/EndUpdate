package com.ehtid.endupdate.entity;

import com.ehtid.endupdate.registry.ModEntities;
import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class EnderArrowEntity extends Arrow {
    public EnderArrowEntity(EntityType<? extends EnderArrowEntity> type, Level level) { super(type, level); }

    public EnderArrowEntity(Level level, LivingEntity shooter) {
        super(ModEntities.ENDER_ARROW.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!level().isClientSide && result.getEntity() instanceof LivingEntity living && living.isAlive()) {
            TeleportUtil.chorusTeleport(living, 8.0D, 8.0D, 16);
        }
    }
}
