package com.ehtid.endupdate.client.renderer;
import com.ehtid.endupdate.EndUpdateMod;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
public class EndCreeperRenderer extends CreeperRenderer {
    private static final ResourceLocation TEX = new ResourceLocation(EndUpdateMod.MODID, "textures/entity/end_creeper.png");
    public EndCreeperRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Creeper entity) { return TEX; }
}
