package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.blockentity.EndTrialCoreBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, EndUpdateMod.MODID);
    public static final RegistryObject<BlockEntityType<EndTrialCoreBlockEntity>> END_TRIAL_CORE = TYPES.register("end_trial_core",
            () -> BlockEntityType.Builder.of(EndTrialCoreBlockEntity::new, ModBlocks.END_TRIAL_CORE.get()).build(null));
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private ModBlockEntities() {}
}
