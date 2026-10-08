package com.ehtid.endupdate.client.model;

import com.ehtid.endupdate.EndUpdateMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public final class EndModelLayers {
    public static final ModelLayerLocation END_ZOMBIE =
            new ModelLayerLocation(new ResourceLocation(EndUpdateMod.MODID, "end_zombie"), "main");
    public static final ModelLayerLocation END_SKELETON =
            new ModelLayerLocation(new ResourceLocation(EndUpdateMod.MODID, "end_skeleton"), "main");
    public static final ModelLayerLocation END_CREEPER =
            new ModelLayerLocation(new ResourceLocation(EndUpdateMod.MODID, "end_creeper"), "main");
    public static final ModelLayerLocation END_SPIDER =
            new ModelLayerLocation(new ResourceLocation(EndUpdateMod.MODID, "end_spider"), "main");

    private EndModelLayers() {}
}
