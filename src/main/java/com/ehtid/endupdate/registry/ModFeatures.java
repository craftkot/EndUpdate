package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.world.EndTrialDungeonFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, EndUpdateMod.MODID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> END_TRIAL_DUNGEON = FEATURES.register("end_trial_dungeon",
            () -> new EndTrialDungeonFeature(NoneFeatureConfiguration.CODEC));
    public static void register(IEventBus bus) { FEATURES.register(bus); }
    private ModFeatures() {}
}
