package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.block.EndTrialCoreBlock;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, EndUpdateMod.MODID);

    private static BlockBehaviour.Properties woodProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    public static final RegistryObject<Block> CHORUS_STEM = BLOCKS.register("chorus_stem",
            () -> new RotatedPillarBlock(woodProps()));
    public static final RegistryObject<Block> CHORUS_PLANKS = BLOCKS.register("chorus_planks",
            () -> new Block(woodProps()));
    public static final RegistryObject<Block> CHORUS_MOSAIC = BLOCKS.register("chorus_mosaic",
            () -> new Block(woodProps()));
    public static final RegistryObject<Block> CHORUS_STAIRS = BLOCKS.register("chorus_stairs",
            () -> new StairBlock(() -> CHORUS_PLANKS.get().defaultBlockState(), woodProps()));
    public static final RegistryObject<Block> CHORUS_SLAB = BLOCKS.register("chorus_slab",
            () -> new SlabBlock(woodProps()));
    public static final RegistryObject<Block> CHORUS_MOSAIC_STAIRS = BLOCKS.register("chorus_mosaic_stairs",
            () -> new StairBlock(() -> CHORUS_MOSAIC.get().defaultBlockState(), woodProps()));
    public static final RegistryObject<Block> CHORUS_MOSAIC_SLAB = BLOCKS.register("chorus_mosaic_slab",
            () -> new SlabBlock(woodProps()));
    public static final RegistryObject<Block> CHORUS_FENCE = BLOCKS.register("chorus_fence",
            () -> new FenceBlock(woodProps()));
    public static final RegistryObject<Block> CHORUS_FENCE_GATE = BLOCKS.register("chorus_fence_gate",
            () -> new FenceGateBlock(woodProps(), WoodType.WARPED));
    public static final RegistryObject<Block> CHORUS_DOOR = BLOCKS.register("chorus_door",
            () -> new DoorBlock(woodProps().noOcclusion(), BlockSetType.WARPED));
    public static final RegistryObject<Block> CHORUS_TRAPDOOR = BLOCKS.register("chorus_trapdoor",
            () -> new TrapDoorBlock(woodProps().noOcclusion(), BlockSetType.WARPED));
    public static final RegistryObject<Block> CHORUS_PRESSURE_PLATE = BLOCKS.register("chorus_pressure_plate",
            () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, woodProps(), BlockSetType.WARPED));
    public static final RegistryObject<Block> CHORUS_BUTTON = BLOCKS.register("chorus_button",
            () -> new ButtonBlock(woodProps().noCollission().strength(0.5F), BlockSetType.WARPED, 30, true));

    public static final RegistryObject<Block> END_TRIAL_CORE = BLOCKS.register("end_trial_core",
            () -> new EndTrialCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F).sound(SoundType.AMETHYST).lightLevel(s -> 7)));

    public static void register(IEventBus bus) { BLOCKS.register(bus); }
    private ModBlocks() {}
}
