package com.ehtid.endupdate.client.model;

import com.ehtid.endupdate.entity.EndSkeleton;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class EndSkeletonModel extends SkeletonModel<EndSkeleton> {
    public EndSkeletonModel(ModelPart root) { super(root); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.ZERO);
        root.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.35F)),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(64, 0).addBox(-4, 0, -2, 8, 12, 4),
                PartPose.ZERO);
        body.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(88, 0).addBox(-4, 0, -2, 8, 12, 4, new CubeDeformation(0.18F)),
                PartPose.ZERO);

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 32).addBox(-1, -2, -1, 2, 12, 2),
                PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(8, 32).mirror().addBox(-1, -2, -1, 2, 12, 2),
                PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(16, 32).addBox(-1, 0, -1, 2, 12, 2),
                PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(24, 32).mirror().addBox(-1, 0, -1, 2, 12, 2),
                PartPose.offset(2, 12, 0));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
