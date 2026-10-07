package com.ehtid.endupdate.client.renderer;
import com.ehtid.endupdate.EndUpdateMod;
import com.ehtid.endupdate.entity.EndZombie;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
public class EndZombieRenderer extends ZombieRenderer {
    private static final ResourceLocation TEX = new ResourceLocation(EndUpdateMod.MODID, "textures/entity/end_zombie.png");
    public EndZombieRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return TEX; }
}
