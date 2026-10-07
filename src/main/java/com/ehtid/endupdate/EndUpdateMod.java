package com.ehtid.endupdate;

import com.ehtid.endupdate.registry.*;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EndUpdateMod.MODID)
public final class EndUpdateMod {
    public static final String MODID = "endupdate";

    public EndUpdateMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModBlockEntities.register(modBus);
        ModEffects.register(modBus);
        ModPotions.register(modBus);
        ModFeatures.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::creativeTabs);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ModPotions.registerBrewing(event);
    }

    private void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.CHORUS_STEM);
            event.accept(ModBlocks.CHORUS_PLANKS);
            event.accept(ModBlocks.CHORUS_MOSAIC);
            event.accept(ModBlocks.CHORUS_STAIRS);
            event.accept(ModBlocks.CHORUS_SLAB);
            event.accept(ModBlocks.CHORUS_MOSAIC_STAIRS);
            event.accept(ModBlocks.CHORUS_MOSAIC_SLAB);
            event.accept(ModBlocks.CHORUS_FENCE);
            event.accept(ModBlocks.CHORUS_FENCE_GATE);
            event.accept(ModBlocks.CHORUS_DOOR);
            event.accept(ModBlocks.CHORUS_TRAPDOOR);
            event.accept(ModBlocks.CHORUS_PRESSURE_PLATE);
            event.accept(ModBlocks.CHORUS_BUTTON);
            event.accept(ModBlocks.END_TRIAL_CORE);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.END_ZOMBIE_SPAWN_EGG);
            event.accept(ModItems.END_SKELETON_SPAWN_EGG);
            event.accept(ModItems.END_CREEPER_SPAWN_EGG);
            event.accept(ModItems.END_SPIDER_SPAWN_EGG);
        }
    }
}
