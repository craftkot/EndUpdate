package com.ehtid.endupdate.client.model;

import com.ehtid.endupdate.entity.EndSpider;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class EndSpiderModel extends SpiderModel<EndSpider> {
    public EndSpiderModel(ModelPart root) { super(root); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -8, 8, 8, 8),
                PartPose.offset(0, 15, -3));
        head.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -4, -8, 8, 8, 8, new CubeDeformation(0.20F)),
                PartPose.ZERO);

        root.addOrReplaceChild("body0",
                CubeListBuilder.create().texOffs(64, 0).addBox(-3, -3, -3, 6, 6, 6),
                PartPose.offset(0, 15, 0));

        PartDefinition body1 = root.addOrReplaceChild("body1",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5, -4, -6, 10, 8, 12),
                PartPose.offset(0, 15, 9));
        body1.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(44, 32).addBox(-5, -4, -6, 10, 8, 12, new CubeDeformation(0.18F)),
                PartPose.ZERO);

        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 64).addBox(-15, -1, -1, 16, 2, 2);
        CubeListBuilder legMirror = CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1, -1, -1, 16, 2, 2);

        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offsetAndRotation(-4, 15, 2, 0, 0.7853982F, -0.7853982F));
        root.addOrReplaceChild("left_hind_leg", legMirror, PartPose.offsetAndRotation(4, 15, 2, 0, -0.7853982F, 0.7853982F));
        root.addOrReplaceChild("right_middle_hind_leg", leg, PartPose.offsetAndRotation(-4, 15, 1, 0, 0.3926991F, -0.58119464F));
        root.addOrReplaceChild("left_middle_hind_leg", legMirror, PartPose.offsetAndRotation(4, 15, 1, 0, -0.3926991F, 0.58119464F));
        root.addOrReplaceChild("right_middle_front_leg", leg, PartPose.offsetAndRotation(-4, 15, 0, 0, -0.3926991F, -0.58119464F));
        root.addOrReplaceChild("left_middle_front_leg", legMirror, PartPose.offsetAndRotation(4, 15, 0, 0, 0.3926991F, 0.58119464F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offsetAndRotation(-4, 15, -1, 0, -0.7853982F, -0.7853982F));
        root.addOrReplaceChild("left_front_leg", legMirror, PartPose.offsetAndRotation(4, 15, -1, 0, 0.7853982F, 0.7853982F));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
