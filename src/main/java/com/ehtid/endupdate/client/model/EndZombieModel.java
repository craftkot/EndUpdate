package com.ehtid.endupdate.client.model;

import com.ehtid.endupdate.entity.EndZombie;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class EndZombieModel extends ZombieModel<EndZombie> {
    public EndZombieModel(ModelPart root) { super(root); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.ZERO);
        head.addOrReplaceChild("forehead_ridge",
                CubeListBuilder.create().texOffs(0, 64).addBox(-2, -7, -5, 4, 2, 1),
                PartPose.ZERO);
        root.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.5F)),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(64, 0).addBox(-4, 0, -2, 8, 12, 4),
                PartPose.ZERO);
        body.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(88, 0).addBox(-4, 0, -2, 8, 12, 4, new CubeDeformation(0.32F)),
                PartPose.ZERO);
        body.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(16, 64).addBox(-2, 4, -3, 4, 4, 1),
                PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 32).addBox(-3, -2, -2, 4, 12, 4),
                PartPose.offset(-5, 2, 0));
        rightArm.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(64, 32).addBox(-3, -2, -2, 4, 12, 4, new CubeDeformation(0.26F)),
                PartPose.ZERO);

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(16, 32).mirror().addBox(-1, -2, -2, 4, 12, 4),
                PartPose.offset(5, 2, 0));
        leftArm.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(80, 32).mirror().addBox(-1, -2, -2, 4, 12, 4, new CubeDeformation(0.26F)),
                PartPose.ZERO);

        PartDefinition rightLeg = root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(32, 32).addBox(-2, 0, -2, 4, 12, 4),
                PartPose.offset(-1.9F, 12, 0));
        rightLeg.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(96, 32).addBox(-2, 0, -2, 4, 12, 4, new CubeDeformation(0.20F)),
                PartPose.ZERO);

        PartDefinition leftLeg = root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(48, 32).mirror().addBox(-2, 0, -2, 4, 12, 4),
                PartPose.offset(1.9F, 12, 0));
        leftLeg.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(112, 32).mirror().addBox(-2, 0, -2, 4, 12, 4, new CubeDeformation(0.20F)),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }
}
