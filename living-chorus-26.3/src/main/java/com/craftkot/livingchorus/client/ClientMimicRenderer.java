package com.craftkot.livingchorus.client;

import com.craftkot.livingchorus.LivingChorus;
import com.craftkot.livingchorus.MimicPosePacket;
import com.craftkot.livingchorus.MimicProfile;
import com.craftkot.livingchorus.MimicRig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.joml.Quaternionf;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * First fully textured in-game visualization. All parts are rigid models with
 * the original Minecraft chorus textures. No client-side entity is spawned:
 * client receives pose snapshots only for ACTIVE attacks.
 *
 * We deliberately do not render anything when idle, so passive branches keep
 * their exact vanilla collision, outlines and radar behavior.
 */
@EventBusSubscriber(modid = LivingChorus.MOD_ID, value = Dist.CLIENT)
public final class ClientMimicRenderer {
    private static final Identifier PLANT_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/chorus_plant.png");
    private static final Identifier FLOWER_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/chorus_flower.png");
    private static final Identifier DEAD_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/chorus_flower_dead.png");

    private static final ContextKey<List<RenderSnapshot>> RENDER_DATA = new ContextKey<>(
        Identifier.fromNamespaceAndPath(LivingChorus.MOD_ID, "mimic_rigs"));

    private static final ModelPart STEM = part("stem", -2, -8, -2, 4, 16, 4);
    private static final ModelPart HEAD = part("head", -7, -7, -7, 14, 14, 14);
    private static final ModelPart UPPER = part("upper", -7, 0, -7, 14, 7, 14);
    private static final ModelPart LOWER = part("lower", -7, -7, -7, 14, 7, 14);
    private static final ModelPart LEFT = part("left", -7, -2, -7, 7, 4, 14);
    private static final ModelPart RIGHT = part("right", 0, -2, -7, 7, 4, 14);

    private static final Map<Long, ClientAttack> ACTIVE = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;

    private record RenderSnapshot(MimicRig.Pose pose, boolean dead, int variant) { }

    private static final class ClientAttack {
        final BlockPos flower;
        final MimicProfile profile;
        final long started;
        final int charge;
        final int recovery;
        final boolean dead;
        final byte kind;
        Vec3 target;

        ClientAttack(MimicPosePacket data, long now) {
            flower = data.flower();
            profile = MimicProfile.fromPacked(data.packedProfile());
            started = now;
            charge = data.chargeTicks();
            recovery = data.recoverTicks();
            dead = data.dead();
            kind = data.kind();
            target = data.target();
        }
    }

    private ClientMimicRenderer() {}

    @SubscribeEvent
    public static void registerPacket(RegisterClientPayloadHandlersEvent event) {
        event.register(MimicPosePacket.TYPE, (packet, context) -> receive(packet));
    }

    private static void receive(MimicPosePacket data) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long key = data.flower().asLong();
        if (data.operation() == MimicPosePacket.STOP) {
            ACTIVE.remove(key);
            return;
        }
        if (data.operation() == MimicPosePacket.UPDATE) {
            ClientAttack current = ACTIVE.get(key);
            if (current != null) current.target = data.target();
        } else if (data.operation() == MimicPosePacket.START) {
            // Never decode or reveal markers for dormant flowers.
            ACTIVE.put(key, new ClientAttack(data, level.getGameTime()));
        }
    }

    /**
     * Build immutable render snapshots on the extraction pass; no direct world
     * reads are permitted in the submit phase of the 26.3 render pipeline.
     */
    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null || previousLevel != level) {
            ACTIVE.clear();
            previousLevel = level;
        }
        if (level == null || ACTIVE.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_DATA, List.of());
            return;
        }
        ArrayList<RenderSnapshot> snapshots = new ArrayList<>();
        double now = level.getGameTime() + event.getRenderState().worldPartialTicks;
        Iterator<ClientAttack> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            ClientAttack attack = it.next();
            double elapsed = Math.max(0, now - attack.started);
            if (elapsed > attack.charge + attack.recovery + 3) {
                it.remove();
                continue;
            }
            boolean recover = elapsed >= attack.charge;
            double progress = recover ? (elapsed - attack.charge) / attack.recovery
                : elapsed / attack.charge;

            Vec3 root = Vec3.atCenterOf(attack.flower.below(2));
            Vec3 initialHead = Vec3.atCenterOf(attack.flower);
            MimicRig.Pose pose = MimicRig.evaluate(
                attack.profile, root, initialHead, attack.target, progress, recover);
            snapshots.add(new RenderSnapshot(pose, attack.dead, attack.profile.mouthVariant()));
        }
        event.getRenderState().setRenderData(RENDER_DATA, List.copyOf(snapshots));
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        List<RenderSnapshot> snapshots = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (snapshots == null || snapshots.isEmpty()) return;

        PoseStack matrices = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        var collector = event.getSubmitNodeCollector();
        final int light = 0x00F000F0; // bright temporary preview, dynamic light later

        for (RenderSnapshot render : snapshots) {
            List<Vec3> path = render.pose.joints();
            for (int i = 1; i < path.size(); i++) {
                Vec3 a = path.get(i - 1), b = path.get(i);
                Vec3 d = b.subtract(a);
                if (d.lengthSqr() < 0.0001) continue;
                Vec3 middle = a.add(b).scale(.5);
                matrices.pushPose();
                matrices.translate(middle.x - camera.x, middle.y - camera.y, middle.z - camera.z);
                matrices.mulPose(new Matrix4f().rotation(new Quaternionf().rotationTo(0, 1, 0,
                    (float)d.x, (float)d.y, (float)d.z)));
                collector.submitModelPart(STEM, matrices, RenderTypes.entityCutout(PLANT_TEXTURE),
                    light, OverlayTexture.NO_OVERLAY, null);
                matrices.popPose();
            }
            Vec3 head = render.pose.mouth();
            matrices.pushPose();
            matrices.translate(head.x - camera.x, head.y - camera.y, head.z - camera.z);
            var flower = RenderTypes.entityCutout(render.dead ? DEAD_TEXTURE : FLOWER_TEXTURE);
            float opening = (float) render.pose.opening();
            if (opening < .04F) {
                collector.submitModelPart(HEAD, matrices, flower,
                    light, OverlayTexture.NO_OVERLAY, null);
            } else {
                submitJaw(collector, matrices, flower, UPPER, opening * .65F, 0F, 0F, light);
                submitJaw(collector, matrices, flower, LOWER, -opening * .65F, 0F, 0F, light);
                submitJaw(collector, matrices, flower, LEFT, 0F, 0F, opening * .55F, light);
                submitJaw(collector, matrices, flower, RIGHT, 0F, 0F, -opening * .55F, light);
            }
            matrices.popPose();
        }
    }

    private static void submitJaw(net.minecraft.client.renderer.SubmitNodeCollector collector,
                                  PoseStack matrices,
                                  net.minecraft.client.renderer.rendertype.RenderType texture,
                                  ModelPart panel, float x, float y, float z, int light) {
        matrices.pushPose();
        matrices.mulPose(new Matrix4f().rotation(new Quaternionf().rotationXYZ(x, y, z)));
        collector.submitModelPart(panel, matrices, texture, light,
            OverlayTexture.NO_OVERLAY, null);
        matrices.popPose();
    }

    private static ModelPart part(String name, float x, float y, float z,
                                  float w, float h, float d) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild(name,
            CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d),
            PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild(name);
    }
}
