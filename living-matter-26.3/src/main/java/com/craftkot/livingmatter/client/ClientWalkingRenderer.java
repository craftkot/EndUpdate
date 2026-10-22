package com.craftkot.livingmatter.client;

import com.craftkot.livingmatter.LivingMatter;
import com.craftkot.livingmatter.MatterWalkPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The moving organism is NOT a spawned mob and does NOT create any extra
 * leaves/stone. Client masks only its actual old/new vanilla block positions
 * and renders the same connected set of full 16px cuboids in-between.
 * All voxels receive the EXACT SAME translation, so they cannot separate.
 */
@EventBusSubscriber(modid = LivingMatter.MOD_ID, value = Dist.CLIENT)
public final class ClientWalkingRenderer {
    private static final ContextKey<List<Snapshot>> KEY = new ContextKey<>(
        Identifier.fromNamespaceAndPath(LivingMatter.MOD_ID, "walker_render"));
    private static final ModelPart CUBE = wholeCube();
    private static final Map<Long, ActiveWalk> ACTIVE = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;

    private record Voxel(Vec3 center, Identifier texture, int light) {}
    private record Snapshot(List<Voxel> voxels) {}

    private static final class ActiveWalk {
        final MatterWalkPacket data;
        final long begun;
        ActiveWalk(MatterWalkPacket data, long begun) {
            this.data = data;
            this.begun = begun;
        }
    }
    private ClientWalkingRenderer() {}

    @SubscribeEvent
    public static void registerPacket(RegisterClientPayloadHandlersEvent event) {
        event.register(MatterWalkPacket.TYPE, (packet, context) -> receive(packet));
    }

    private static void receive(MatterWalkPacket packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || packet.from().isEmpty()) return;
        if (previousLevel != level) {
            ACTIVE.clear();
            previousLevel = level;
        }
        BlockPos marker = packet.from().get(0);
        ACTIVE.put(marker.asLong(), new ActiveWalk(packet, level.getGameTime()));
        ClientMatterMask.startWalk(level, marker, packet.from(), packet.dx(), packet.dz());
    }

    @SubscribeEvent
    public static void extract(ExtractLevelRenderStateEvent event) {
        var level = Minecraft.getInstance().level;
        if (level == null || previousLevel != level) {
            if (previousLevel != null) {
                for (ActiveWalk walk : ACTIVE.values())
                    ClientMatterMask.stopWalk(previousLevel, walk.data.from().get(0));
            }
            ACTIVE.clear();
            previousLevel = level;
        }
        if (level == null || ACTIVE.isEmpty()) {
            event.getRenderState().setRenderData(KEY, List.of());
            return;
        }
        double now = level.getGameTime() + event.getRenderState().worldPartialTicks;
        ArrayList<Snapshot> frames = new ArrayList<>();
        Iterator<ActiveWalk> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            ActiveWalk active = iterator.next();
            MatterWalkPacket packet = active.data;
            double elapsed = now - active.begun;
            if (elapsed >= packet.duration() + 2.0) {
                ClientMatterMask.stopWalk(level, packet.from().get(0));
                iterator.remove();
                continue;
            }
            double t = Math.max(0,Math.min(1,elapsed / packet.duration()));
            // Ease without sudden acceleration; subtle unified step bounce.
            double smooth = t*t*(3-2*t);
            double bounce = Math.sin(t*Math.PI)*.10;
            ArrayList<Voxel> voxels = new ArrayList<>();
            for (int i=0;i<packet.from().size();i++) {
                BlockPos from = packet.from().get(i);
                Vec3 position = Vec3.atCenterOf(from).add(
                    packet.dx() * smooth, bounce, packet.dz() * smooth);
                int light = LightCoordsUtil.pack(
                    level.getBrightness(LightLayer.BLOCK, BlockPos.containing(position)),
                    level.getBrightness(LightLayer.SKY, BlockPos.containing(position)));
                Identifier texture = lookupTexture(packet.textures().get(i));
                voxels.add(new Voxel(position,texture,light));
            }
            frames.add(new Snapshot(List.copyOf(voxels)));
        }
        event.getRenderState().setRenderData(KEY, List.copyOf(frames));
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        List<Snapshot> snapshots = event.getLevelRenderState().getRenderData(KEY);
        if (snapshots == null || snapshots.isEmpty()) return;
        PoseStack matrices = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        var collector = event.getSubmitNodeCollector();
        for (Snapshot snapshot : snapshots) {
            for (Voxel voxel : snapshot.voxels) {
                matrices.pushPose();
                matrices.translate(voxel.center.x-camera.x,
                    voxel.center.y-camera.y,voxel.center.z-camera.z);
                collector.submitModelPart(CUBE, matrices,
                    RenderTypes.entityCutout(voxel.texture),
                    voxel.light, OverlayTexture.NO_OVERLAY, null);
                matrices.popPose();
            }
        }
    }

    private static Identifier lookupTexture(String registry) {
        int split = registry.indexOf(':');
        if (split < 0) return Identifier.fromNamespaceAndPath(
            "minecraft","textures/block/stone.png");
        String ns = registry.substring(0,split);
        String name = registry.substring(split+1);
        // Original unmodified sprite: no new PNGs, no per-face scaling.
        if (!ns.matches("[a-z0-9_.-]+") || !name.matches("[a-z0-9_./-]+"))
            return Identifier.fromNamespaceAndPath("minecraft","textures/block/stone.png");
        return Identifier.fromNamespaceAndPath(ns,"textures/block/"+name+".png");
    }

    /** Six one-sided 16x16 faces, each sampling the whole original texture.
     * The normal box-UV atlas layout would stretch/crop Minecraft PNGs. */
    private static ModelPart wholeCube() {
        MeshDefinition mesh = new MeshDefinition();
        float x=-8,y=-8,z=-8,w=16,h=16,d=16;
        for (Direction face : Direction.values()) {
            float faceOffsetU = switch (face) {
                case WEST -> 0F;
                case NORTH, UP -> d;
                case EAST, DOWN -> d+w;
                case SOUTH -> 2F*d+w;
            };
            float faceOffsetV = switch (face) {
                case UP, DOWN -> 0F;
                default -> d;
            };
            CubeListBuilder faceOnly = CubeListBuilder.create()
                .texOffs(Math.round(-faceOffsetU),Math.round(-faceOffsetV))
                .addBox(x,y,z,w,h,d,Set.of(face));
            mesh.getRoot().addOrReplaceChild(
                "material_"+face.getName(),faceOnly,PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,16,16).bakeRoot();
    }
}
