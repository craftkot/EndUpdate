package com.ehtid.endupdate.effect;

import com.ehtid.endupdate.util.TeleportUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class ChorusTeleportEffect extends MobEffect {
    public ChorusTeleportEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8a56b3);
    }

    @Override
    public void applyEffectTick(LivingEntity target, int amplifier) {
        TeleportUtil.chorusTeleport(target, 8.0D + amplifier * 2.0D, 8.0D, 16);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
