package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.effect.ChorusTeleportEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, EndUpdateMod.MODID);
    public static final RegistryObject<MobEffect> CHORUS_TELEPORT = EFFECTS.register("chorus_teleport", ChorusTeleportEffect::new);
    public static void register(IEventBus bus) { EFFECTS.register(bus); }
    private ModEffects() {}
}
