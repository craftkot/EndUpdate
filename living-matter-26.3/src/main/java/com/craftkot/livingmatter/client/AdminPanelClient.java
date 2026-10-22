package com.craftkot.livingmatter.client;

import com.craftkot.livingmatter.AdminPanelPacket;
import com.craftkot.livingmatter.LivingMatter;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid=LivingMatter.MOD_ID,value=Dist.CLIENT)
public final class AdminPanelClient {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(
            LivingMatter.MOD_ID,"admin"));
    public static final KeyMapping PANEL_KEY=new KeyMapping(
        "key.livingmatter.admin_panel",InputConstants.Type.KEYBOARD,
        InputConstants.KEY_F8,CATEGORY);

    private AdminPanelClient(){}

    @SubscribeEvent
    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(PANEL_KEY);
    }

    @SubscribeEvent
    public static void registerPacket(RegisterClientPayloadHandlersEvent event) {
        event.register(AdminPanelPacket.TYPE,(packet,ctx)->open(packet));
    }

    private static void open(AdminPanelPacket packet) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        if(mc.gui.screen() instanceof AdminPanelScreen panel) panel.accept(packet);
        else mc.gui.setScreen(new AdminPanelScreen(packet));
    }

    @SubscribeEvent
    public static void key(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        while(PANEL_KEY.consumeClick()) {
            if(mc.getConnection()==null||mc.player==null)continue;
            if(mc.gui.screen() instanceof AdminPanelScreen existing) existing.onClose();
            else mc.getConnection().sendCommand("livingmatter panel");
        }
    }
}
