package com.ehtid.endupdate.client.renderer;
import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.entity.EndSpider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;
public class EndSpiderRenderer extends SpiderRenderer<EndSpider> {
    private static final ResourceLocation TEX = new ResourceLocation(EndUpdateMod.MODID, "textures/entity/end_spider.png");
    public EndSpiderRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(EndSpider entity) { return TEX; }
}
