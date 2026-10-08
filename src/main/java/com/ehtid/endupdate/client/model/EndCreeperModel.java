package com.ehtid.endupdate.client.model;

import com.ehtid.endupdate.entity.EndCreeper;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class EndCreeperModel extends CreeperModel<EndCreeper> {
    public EndCreeperModel(ModelPart root) { super(root); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, 6, 0));
        head.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, new CubeDeformation(0.35F)),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(64, 0).addBox(-4, 0, -2, 8, 12, 4),
                PartPose.offset(0, 6, 0));
        body.addOrReplaceChild("second_layer",
                CubeListBuilder.create().texOffs(88, 0).addBox(-4, 0, -2, 8, 12, 4, new CubeDeformation(0.22F)),
                PartPose.ZERO);

        root.addOrReplaceChild("right_hind_leg",
                CubeListBuilder.create().texOffs(0, 32).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(-2, 18, 4));
        root.addOrReplaceChild("left_hind_leg",
                CubeListBuilder.create().texOffs(16, 32).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(2, 18, 4));
        root.addOrReplaceChild("right_front_leg",
                CubeListBuilder.create().texOffs(32, 32).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(-2, 18, -4));
        root.addOrReplaceChild("left_front_leg",
                CubeListBuilder.create().texOffs(48, 32).addBox(-2, 0, -2, 4, 6, 4),
                PartPose.offset(2, 18, -4));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
