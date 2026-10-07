package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EndUpdateMod.MODID);

    static {
        block("chorus_stem", ModBlocks.CHORUS_STEM);
        block("chorus_planks", ModBlocks.CHORUS_PLANKS);
        block("chorus_mosaic", ModBlocks.CHORUS_MOSAIC);
        block("chorus_stairs", ModBlocks.CHORUS_STAIRS);
        block("chorus_slab", ModBlocks.CHORUS_SLAB);
        block("chorus_mosaic_stairs", ModBlocks.CHORUS_MOSAIC_STAIRS);
        block("chorus_mosaic_slab", ModBlocks.CHORUS_MOSAIC_SLAB);
        block("chorus_fence", ModBlocks.CHORUS_FENCE);
        block("chorus_fence_gate", ModBlocks.CHORUS_FENCE_GATE);
        block("chorus_door", ModBlocks.CHORUS_DOOR);
        block("chorus_trapdoor", ModBlocks.CHORUS_TRAPDOOR);
        block("chorus_pressure_plate", ModBlocks.CHORUS_PRESSURE_PLATE);
        block("chorus_button", ModBlocks.CHORUS_BUTTON);
        block("end_trial_core", ModBlocks.END_TRIAL_CORE);
    }

    public static final RegistryObject<Item> END_ZOMBIE_SPAWN_EGG = ITEMS.register("end_zombie_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.END_ZOMBIE, 0x34264f, 0x8d6bb5, new Item.Properties()));
    public static final RegistryObject<Item> END_SKELETON_SPAWN_EGG = ITEMS.register("end_skeleton_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.END_SKELETON, 0x514763, 0xc9b6dd, new Item.Properties()));
    public static final RegistryObject<Item> END_CREEPER_SPAWN_EGG = ITEMS.register("end_creeper_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.END_CREEPER, 0x33224a, 0xa66bd7, new Item.Properties()));
    public static final RegistryObject<Item> END_SPIDER_SPAWN_EGG = ITEMS.register("end_spider_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.END_SPIDER, 0x20192c, 0x9b5ec5, new Item.Properties()));

    private static RegistryObject<Item> block(String name, RegistryObject<? extends net.minecraft.world.level.block.Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); }
    private ModItems() {}
}
