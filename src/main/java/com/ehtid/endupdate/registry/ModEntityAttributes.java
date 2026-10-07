package com.ehtid.endupdate.registry;

import com.ehtid.endupdate.EndUpdateMod;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EndUpdateMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.END_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(ModEntities.END_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(ModEntities.END_CREEPER.get(), Creeper.createAttributes().build());
        event.put(ModEntities.END_SPIDER.get(), Spider.createAttributes().build());
    }
}
