package com.craftkot.livingmatter.client;

import com.craftkot.livingmatter.ChthonicMassEntity;
import com.craftkot.livingmatter.LivingMatter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Procedurally shapes EVERY consumed source block into one chthonic anatomy:
 * torso, huge hinged maw, crawling legs and twitching feelers.
 *
 * No model is a gigantic rescaled cube; each body voxel is 1 vanilla block
 * with a 16x16 UV face. More infected blocks -> additional REAL body volume.
 */
@EventBusSubscriber(modid = LivingMatter.MOD_ID, value = Dist.CLIENT)
public final class ChthonicMassRenderer
    extends EntityRenderer<ChthonicMassEntity,ChthonicMassRenderer.State> {
    private static final ModelPart VOXEL = part();
    private static final Identifier TEETH = texture("calcite");
    private static final Identifier THROAT = texture("sculk");
    private static final Identifier OOZE = texture("crying_obsidian");

    public static final class State extends EntityRenderState {
        public int blocks=4;
        public int material=0;
        public float age=0f;
        public float facing=0f;
        public int light=LightCoordsUtil.pack(12,12);
    }

    public ChthonicMassRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius=.75f;
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LivingMatter.CHTHONIC_MASS.get(),ChthonicMassRenderer::new);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        register(event);
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(ChthonicMassEntity entity,State state,float partialTick) {
        super.extractRenderState(entity,state,partialTick);
        state.blocks=entity.mass();
        state.material=entity.material();
        state.age=entity.tickCount+partialTick;
        state.facing=entity.getYRot();
        state.light=LightCoordsUtil.pack(
            entity.level().getBrightness(LightLayer.BLOCK,entity.blockPosition()),
            entity.level().getBrightness(LightLayer.SKY,entity.blockPosition()));
    }

    @Override
    public void submit(State state,PoseStack pose,SubmitNodeCollector collector,
                       CameraRenderState camera) {
        super.submit(state,pose,collector,camera);
        int n=state.blocks;
        float age=state.age;
        Identifier skin = material(state.material);
        float size=(float)Math.cbrt(n);
        float w=.52f+size*.29f, h=.8f+size*.29f;
        pose.pushPose();
        pose.mulPose(new Matrix4f().rotationY((float)Math.toRadians(-state.facing+180)));
        // Entire source colony participates. Repetition of a block unit is
        // forbidden: every index selects exactly one body part and one model.
        int torso=Math.max(1,(int)(n*.58));
        int jaws=Math.max(2,(int)(n*.19));
        int limbs=Math.max(1,(int)(n*.15));
        while(torso+jaws+limbs>n) torso--;
        int feelers=n-torso-jaws-limbs;

        List<float[]> body=buildBody(torso,size);
        for(float[] v:body) {
            emit(pose,collector,skin,state.light,v[0],v[1],v[2],0,0,0);
        }

        // The original infected blocks OPEN to reveal a living split jaw.
        // Both mandibles swing but never generate fresh blocks.
        float open=.35f+(float)(.31*Math.sin(age*.10));
        for(int j=0;j<jaws;j++) {
            int row=j/2;
            boolean upper=(j&1)==0;
            float spread=(row-(jaws/2-1)*.5f)*.72f;
            float lip=upper?.5f:-.5f;
            float y=1.1f+h*.54f+lip+ (upper?open:-open);
            float z=-w-.6f - Math.abs(spread)*.04f;
            Identifier tex=(j%5==0)?TEETH:(j%3==0)?THROAT:skin;
            emit(pose,collector,tex,state.light,spread,y,z,
                upper?-.1f:.1f,0,0);
        }

        // Crawling forelegs/knuckles. Taller creatures gain more articulated
        // legs using the extra infected blocks, with synchronized gait.
        for(int i=0;i<limbs;i++) {
            int leg=i%4;
            int segment=i/4;
            float side=(leg%2==0)?-1:1;
            float forward=(leg<2)?-1:1;
            float phase=(float)Math.sin(age*.16+(leg/2)*Math.PI);
            float x=side*(w+.35f+segment*.18f);
            float y=.52f+segment*.6f+Math.max(0,phase)*.25f;
            float z=forward*(w*.55f+segment*.12f)+phase*.23f;
            emit(pose,collector,(i%6==0)?OOZE:skin,state.light,x,y,z,
                (float)(phase*.17),0,(float)(side*.09));
        }

        // Asymmetric upper tendrils evoke an abyssal, multi-mouthed creature.
        for(int i=0;i<feelers;i++) {
            int feeler=i%4, segment=i/4;
            float side=(feeler%2==0)?-1:1;
            float back=(feeler<2)?1:-1;
            float sway=(float)Math.sin(age*.09+feeler*.88+segment*.25);
            float x=side*(w*.6f+segment*.24f)+sway*.20f;
            float y=1.2f+h*.9f+segment*.67f;
            float z=back*(w*.4f+segment*.14f)+sway*.16f;
            emit(pose,collector,(i%3==0)?OOZE:skin,state.light,x,y,z,
                sway*.1f,sway*.12f,0);
        }
        pose.popPose();
    }

    private static List<float[]> buildBody(int count,float size) {
        int rx=Math.max(1,Math.round(size*.48f));
        int ry=Math.max(1,Math.round(size*.36f));
        int rz=Math.max(1,Math.round(size*.39f));
        ArrayList<float[]> list=new ArrayList<>();
        for(int y=-ry;y<=ry;y++)
            for(int z=-rz;z<=rz;z++)
                for(int x=-rx;x<=rx;x++){
                    float ux=(float)x/Math.max(1,rx), uy=(float)y/Math.max(1,ry),
                        uz=(float)z/Math.max(1,rz);
                    float ellipsoid=ux*ux*.9f+uy*uy*.72f+uz*uz*.9f;
                    list.add(new float[]{x*.79f,1.35f+ry*.63f+y*.81f,z*.79f,
                        ellipsoid+((x*13+y*19+z*7)&7)*.012f});
                }
        list.sort(Comparator.comparingDouble(v->v[3]));
        // Bounded to the actual number of original blocks; more mass means
        // voxels joining the body, never an inflated/stretch-textured cube.
        while(list.size()<count) {
            int i=list.size();
            float theta=i*2.39996f;
            float r=(float)Math.sqrt(i/count)*size*.61f;
            list.add(new float[]{(float)Math.cos(theta)*r,
                1.2f+size*.54f+(i%3)*.71f,
                (float)Math.sin(theta)*r,0f});
        }
        return list.subList(0,count);
    }

    private static void emit(PoseStack pose,SubmitNodeCollector collector,
                             Identifier texture,int light,
                             float x,float y,float z,float pitch,float yaw,float roll){
        pose.pushPose();
        pose.translate(x,y,z);
        if(pitch!=0||yaw!=0||roll!=0)
            pose.mulPose(new Matrix4f().rotation(new Quaternionf().rotationXYZ(pitch,yaw,roll)));
        collector.submitModelPart(VOXEL,pose,RenderTypes.entityCutout(texture),
            light,OverlayTexture.NO_OVERLAY,null);
        pose.popPose();
    }

    private static Identifier material(int i){
        String path=switch(i){
            case 1->"stripped_dark_oak_log"; case 2->"mud";
            case 3->"deepslate_iron_ore"; case 4->"dark_oak_leaves";
            case 5->"sculk"; case 6->"crying_obsidian";
            default->"deepslate";
        };
        return texture(path);
    }
    private static Identifier texture(String path){
        return Identifier.fromNamespaceAndPath("minecraft","textures/block/"+path+".png");
    }

    private static ModelPart part(){
        MeshDefinition mesh=new MeshDefinition();
        final float x=-8,y=-8,z=-8,w=16,h=16,d=16;
        for(Direction face:Direction.values()){
            float u=switch(face){
                case WEST->0;case NORTH,UP->d;case EAST,DOWN->d+w;case SOUTH->2*d+w;
            };
            float v=(face==Direction.UP||face==Direction.DOWN)?0:d;
            mesh.getRoot().addOrReplaceChild("voxel_"+face.getName(),
                CubeListBuilder.create().texOffs(Math.round(-u),Math.round(-v))
                    .addBox(x,y,z,w,h,d,Set.of(face)),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,16,16).bakeRoot();
    }
}
