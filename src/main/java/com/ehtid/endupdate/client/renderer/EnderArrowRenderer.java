package com.ehtid.endupdate.client.renderer;
import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.entity.EnderArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
public class EnderArrowRenderer extends ArrowRenderer<EnderArrowEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(EndUpdateMod.MODID, "textures/entity/ender_arrow.png");
    public EnderArrowRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(EnderArrowEntity entity) { return TEX; }
}
