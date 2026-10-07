package com.ehtid.endupdate.effect;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class ChorusTeleportEffect extends InstantenousMobEffect {
    public ChorusTeleportEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8a56b3);
    }

    @Override
    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity indirectSource,
                                         LivingEntity target, int amplifier, double health) {
        TeleportUtil.chorusTeleport(target, 8.0D + amplifier * 2.0D, 8.0D, 16);
    }
}
