package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EndUpdateMod.MODID);

    public static final RegistryObject<EntityType<EndZombie>> END_ZOMBIE = ENTITIES.register("end_zombie",
            () -> EntityType.Builder.of(EndZombie::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8).build("end_zombie"));
    public static final RegistryObject<EntityType<EndSkeleton>> END_SKELETON = ENTITIES.register("end_skeleton",
            () -> EntityType.Builder.of(EndSkeleton::new, MobCategory.MONSTER).sized(0.6F, 1.99F).clientTrackingRange(8).build("end_skeleton"));
    public static final RegistryObject<EntityType<EndCreeper>> END_CREEPER = ENTITIES.register("end_creeper",
            () -> EntityType.Builder.of(EndCreeper::new, MobCategory.MONSTER).sized(0.6F, 1.7F).clientTrackingRange(8).build("end_creeper"));
    public static final RegistryObject<EntityType<EndSpider>> END_SPIDER = ENTITIES.register("end_spider",
            () -> EntityType.Builder.of(EndSpider::new, MobCategory.MONSTER).sized(1.4F, 0.9F).clientTrackingRange(8).build("end_spider"));
    public static final RegistryObject<EntityType<EnderArrowEntity>> ENDER_ARROW = ENTITIES.register("ender_arrow",
            () -> EntityType.Builder.<EnderArrowEntity>of(EnderArrowEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(4).updateInterval(20).build("ender_arrow"));

    public static void register(IEventBus bus) { ENTITIES.register(bus); }
    private ModEntities() {}
}
