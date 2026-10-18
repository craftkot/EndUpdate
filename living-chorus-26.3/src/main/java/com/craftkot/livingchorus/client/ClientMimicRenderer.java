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
import net.minecraft.world.level.LightLayer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import java.util.Set;

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

    // Pixel dimensions of rigid parts. 10px stems overlap 0.64-block joints
    // slightly without changing UV scale or elongating cuboids.
    private static final ModelPart STEM = part("stem", -2, -8, -2, 4, 16, 4);
    private static final ModelPart HEAD = part("head", -7, -7, -7, 14, 14, 14);
    // Four nonoverlapping CLOSED sections of a 14x14x14 flower cube.
    // No exterior caps or cover cubes are added in the sleeping state.
    private static final ModelPart UPPER = part("upper", -7, 1, -7, 14, 6, 14);
    private static final ModelPart LOWER = part("lower", -7, -7, -7, 14, 6, 14);
    private static final ModelPart LEFT = part("left", -7, -1, -7, 6, 2, 14);
    private static final ModelPart RIGHT = part("right", 1, -1, -7, 6, 2, 14);
    private static final ModelPart CORE = part("core", -1, -1, -4, 2, 2, 8);
    // Teeth are visible only AFTER opening; hidden completely by HEAD when shut.
    private static final ModelPart TOP_TEETH = part("top_teeth", -4, 0, -5, 8, 2, 2);
    private static final ModelPart BOTTOM_TEETH = part("bottom_teeth", -4, -2, -5, 8, 2, 2);

    private static final Map<Long, ClientAttack> ACTIVE = new HashMap<>();
    // The CI smoke sets this environment variable. Real gameplay does not
    // produce per-frame logs and dormant mimics remain undisclosed.
    private static final boolean SMOKE = "1".equals(System.getenv("LIVING_CHORUS_SMOKE"));
    private static long lastExtractLog = Long.MIN_VALUE;
    private static long lastSubmitLog = Long.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;

    private record RenderSnapshot(MimicRig.Pose pose, Vec3 target,
                                  boolean dead, int variant,
                                  List<Integer> segmentLight, int mouthLight) { }

    private static final class ClientAttack {
        final BlockPos flower;
        final MimicProfile profile;
        final java.util.List<BlockPos> branch;
        final long started;
        final int charge;
        final int recovery;
        final boolean dead;
        final byte kind;
        Vec3 target;

        ClientAttack(MimicPosePacket data, long now) {
            flower = data.flower();
            profile = MimicProfile.fromPacked(data.packedProfile());
            branch = java.util.List.copyOf(data.branch());
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
        // A payload may arrive before the first render extraction after entering
        // The End. Bind the world here, otherwise extraction erases that first attack.
        if (previousLevel != level) {
            if (previousLevel != null) ClientMimicBlockMask.reset(previousLevel);
            ACTIVE.clear();
            previousLevel = level;
        }
        long key = data.flower().asLong();
        if (SMOKE) System.out.println("LC_SMOKE_PACKET op=" + data.operation()
            + " flower=" + data.flower() + " kind=" + data.kind()
            + " target=" + data.target() + " active_before=" + ACTIVE.size());
        if (data.operation() == MimicPosePacket.STOP) {
            ACTIVE.remove(key);
            ClientMimicBlockMask.stop(level, data.flower());
            return;
        }
        if (data.operation() == MimicPosePacket.UPDATE) {
            ClientAttack current = ACTIVE.get(key);
            if (current != null) current.target = data.target();
        } else if (data.operation() == MimicPosePacket.START) {
            // Never decode or reveal markers for dormant flowers.
            ACTIVE.put(key, new ClientAttack(data, level.getGameTime()));
            ClientMimicBlockMask.start(level, data.flower(), data.branch());
            if (SMOKE) System.out.println("LC_SMOKE_MASK_STARTED flower=" + data.flower());
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
            if (previousLevel != null) ClientMimicBlockMask.reset(previousLevel);
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
                ClientMimicBlockMask.stop(level, attack.flower);
                continue;
            }
            boolean recover = elapsed >= attack.charge;
            double progress = recover ? (elapsed - attack.charge) / attack.recovery
                : elapsed / attack.charge;

            java.util.List<Vec3> resting = attack.branch.stream().map(Vec3::atCenterOf).toList();
            MimicRig.Pose pose = MimicRig.evaluateBranch(
                attack.profile, resting, attack.target, progress, recover);
            // Light must be sampled here, on the extraction/client world pass.
            // SubmitCustomGeometryEvent must not consult the world or light engine.
            ArrayList<Integer> segmentLight = new ArrayList<>();
            for (int segment = 1; segment < pose.joints().size(); segment++) {
                Vec3 a = pose.joints().get(segment - 1), b = pose.joints().get(segment);
                Vec3 middle = a.add(b).scale(.5);
                segmentLight.add(sampleWorldLight(level, middle));
            }
            int mouthLight = sampleWorldLight(level, pose.mouth());
            snapshots.add(new RenderSnapshot(pose, attack.target,
                attack.dead, attack.profile.mouthVariant(), List.copyOf(segmentLight), mouthLight));
        }
        event.getRenderState().setRenderData(RENDER_DATA, List.copyOf(snapshots));
        if (SMOKE && !snapshots.isEmpty() && level.getGameTime() != lastExtractLog) {
            lastExtractLog = level.getGameTime();
            var sample = snapshots.get(0);
            System.out.println("LC_SMOKE_EXTRACT count=" + snapshots.size()
                + " flower_head=" + sample.pose().mouth()
                + " joints=" + sample.pose().joints().size()
                + " opening=" + sample.pose().opening()
                + " camera=" + event.getRenderState().cameraRenderState.pos);
        }
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        List<RenderSnapshot> snapshots = event.getLevelRenderState().getRenderData(RENDER_DATA);
        if (snapshots == null || snapshots.isEmpty()) return;

        PoseStack matrices = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        var collector = event.getSubmitNodeCollector();

        if (SMOKE && Minecraft.getInstance().level != null
            && Minecraft.getInstance().level.getGameTime() != lastSubmitLog) {
            lastSubmitLog = Minecraft.getInstance().level.getGameTime();
            System.out.println("LC_SMOKE_SUBMIT snapshots=" + snapshots.size()
                + " camera=" + camera + " collector=" + collector.getClass().getSimpleName());
        }

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
                    render.segmentLight().get(i - 1), OverlayTexture.NO_OVERLAY, null);
                matrices.popPose();
            }
            Vec3 head = render.pose.mouth();
            matrices.pushPose();
            matrices.translate(head.x - camera.x, head.y - camera.y, head.z - camera.z);
            Vec3 toPrey = render.target.subtract(head);
            if (toPrey.horizontalDistanceSqr() > .001) {
                float facing = (float) Math.atan2(-toPrey.x, -toPrey.z);
                matrices.mulPose(new Matrix4f().rotationY(facing));
            }
            var flower = RenderTypes.entityCutout(render.dead ? DEAD_TEXTURE : FLOWER_TEXTURE);
            float opening = (float) render.pose.opening();
            int light = render.mouthLight();
            if (opening < .04F) {
                // Closed mimic = ONE head, not four separately visible jaw cuboids.
                collector.submitModelPart(HEAD, matrices, flower,
                    light, OverlayTexture.NO_OVERLAY, null);
            } else {
                // Hinge locations are the OUTER perimeter of the 14px head.
                // Local hinge coordinates are in model pixels, converted by /16.
                float jawAmplitude = .14F + .88F * opening;
                float sideAmplitude = .12F + .70F * opening;
                // Top/bottom rotate about their outer horizontal edges.
                submitJaw(collector, matrices, flower, UPPER,
                    0F, 7F, 0F, jawAmplitude, 0F, 0F, light);
                submitJaw(collector, matrices, flower, LOWER,
                    0F, -7F, 0F, -jawAmplitude, 0F, 0F, light);
                // Left/right swing about the two outer vertical edges, not Z.
                submitJaw(collector, matrices, flower, LEFT,
                    -7F, 0F, 0F, 0F, sideAmplitude, 0F, light);
                submitJaw(collector, matrices, flower, RIGHT,
                    7F, 0F, 0F, 0F, -sideAmplitude, 0F, light);
                // This uses the original vanilla plant pixels for the interior,
                // never a newly painted texture or an extra cosmetic overlay.
                collector.submitModelPart(CORE, matrices,
                    RenderTypes.entityCutout(PLANT_TEXTURE), light,
                    OverlayTexture.NO_OVERLAY, null);
                // Small internal teeth are submitted ONLY after jaws visibly open.
                if (opening > .23F) {
                    collector.submitModelPart(TOP_TEETH, matrices,
                        RenderTypes.entityCutout(render.dead ? DEAD_TEXTURE : FLOWER_TEXTURE),
                        light, OverlayTexture.NO_OVERLAY, null);
                    collector.submitModelPart(BOTTOM_TEETH, matrices,
                        RenderTypes.entityCutout(render.dead ? DEAD_TEXTURE : FLOWER_TEXTURE),
                        light, OverlayTexture.NO_OVERLAY, null);
                }
            }
            matrices.popPose();
        }
    }

    private static int sampleWorldLight(net.minecraft.client.multiplayer.ClientLevel level,
                                        Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        return LightCoordsUtil.pack(block, sky);
    }

    private static void submitJaw(net.minecraft.client.renderer.SubmitNodeCollector collector,
                                  PoseStack matrices,
                                  net.minecraft.client.renderer.rendertype.RenderType texture,
                                  ModelPart panel,
                                  float pivotXpx, float pivotYpx, float pivotZpx,
                                  float x, float y, float z, int light) {
        matrices.pushPose();
        // ModelPart boxes are authored in 16px block coordinates.
        // Rotate around the shared OUTER hinge so plates physically split,
        // instead of spinning through each other at the center.
        matrices.translate(pivotXpx / 16F, pivotYpx / 16F, pivotZpx / 16F);
        matrices.mulPose(new Matrix4f().rotation(new Quaternionf().rotationXYZ(x, y, z)));
        matrices.translate(-pivotXpx / 16F, -pivotYpx / 16F, -pivotZpx / 16F);
        collector.submitModelPart(panel, matrices, texture, light,
            OverlayTexture.NO_OVERLAY, null);
        matrices.popPose();
    }

    /**
     * Bake each face as an independently UV-positioned visible quad on the
     * ORIGINAL 16x16 vanilla texture. A normal ModelPart cube unwraps 6 faces
     * in one long horizontal atlas strip, which overruns a 16x16 block sprite.
     * The six one-sided copies let each face use [0,16] without cropping,
     * tiling, a second atlas or new texture pixels.
     *
     * Critical: each jaw panel reuses the UV coordinates it occupied when
     * the flower was intact. The picture remains seamless in closed pose and
     * each panel is rigid during articulation (no stretch).
     *
     * Coordinate origin: model flower centered at (0,0,0), outer corners
     * [-7,7]. Source texture borders are reserved, map [-7,7] -> [1,15].
     */
    private static ModelPart part(String name, float x, float y, float z,
                                  float w, float h, float d) {
        MeshDefinition mesh = new MeshDefinition();
        for (Direction face : Direction.values()) {
            // Position of this cuboid's face inside the original 16px flower.
            // NORTH/SOUTH: x/y; EAST/WEST: z/y; UP/DOWN: x/z.
            float desiredU = switch (face) {
                case EAST, WEST -> z + 8F;
                default -> x + 8F;
            };
            float desiredV = switch (face) {
                case UP, DOWN -> z + 8F;
                default -> y + 8F;
            };

            // Mojang ModelPart.Cube layout for a box with width w and depth d:
            // left [0,d], north/up [d,d+w], right/down [d+w,d+2w],
            // south [2d+w,2d+2w]; side faces begin at v=d, caps at v=0.
            float faceOffsetU = switch (face) {
                case WEST -> 0F;
                case NORTH, UP -> d;
                case EAST, DOWN -> d + w;
                case SOUTH -> 2F * d + w;
            };
            float faceOffsetV = switch (face) {
                case DOWN, UP -> 0F;
                default -> d;
            };

            // Integer texture coordinates preserve pixel alignment exactly.
            int u = Math.round(desiredU - faceOffsetU);
            int v = Math.round(desiredV - faceOffsetV);
            CubeListBuilder faceOnly = CubeListBuilder.create().texOffs(u, v)
                .addBox(x, y, z, w, h, d, Set.of(face));
            mesh.getRoot().addOrReplaceChild(name + "_" + face.getName(),
                faceOnly, PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 16, 16).bakeRoot();
    }
}
