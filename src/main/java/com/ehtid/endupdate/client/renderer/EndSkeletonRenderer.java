package com.ehtid.endupdate.client.renderer;
import com.ehtid.endupdate.EndUpdateMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
public class EndSkeletonRenderer extends SkeletonRenderer {
    private static final ResourceLocation TEX = new ResourceLocation(EndUpdateMod.MODID, "textures/entity/end_skeleton.png");
    public EndSkeletonRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.AbstractSkeleton entity) { return TEX; }
}
