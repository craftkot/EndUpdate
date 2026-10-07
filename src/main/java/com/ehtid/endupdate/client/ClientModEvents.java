package com.ehtid.endupdate.client;

import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.client.renderer.*;
import com.ehtid.endupdate.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EndUpdateMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.END_ZOMBIE.get(), EndZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.END_SKELETON.get(), EndSkeletonRenderer::new);
        event.registerEntityRenderer(ModEntities.END_CREEPER.get(), EndCreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.END_SPIDER.get(), EndSpiderRenderer::new);
        event.registerEntityRenderer(ModEntities.ENDER_ARROW.get(), EnderArrowRenderer::new);
    }
}
