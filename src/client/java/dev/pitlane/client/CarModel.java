package dev.pitlane.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class CarModel extends EntityModel<CarRenderState> {
    public CarModel() {
        super(createRoot(), texture -> RenderTypes.entityCutout(texture));
    }

    private static ModelPart createRoot() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-12, 6, -22, 24, 7, 44), PartPose.ZERO);
        root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 56)
                .addBox(-10, 11, 2, 20, 4, 16), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(144, 0)
                .addBox(-8, 12, -9, 16, 7, 18), PartPose.ZERO);
        root.addOrReplaceChild("front_bumper", CubeListBuilder.create().texOffs(144, 52)
                .addBox(-12, 6, 22, 24, 3, 2), PartPose.ZERO);
        root.addOrReplaceChild("rear_bumper", CubeListBuilder.create().texOffs(144, 60)
                .addBox(-12, 6, -24, 24, 3, 2), PartPose.ZERO);
        root.addOrReplaceChild("wheel_front_left", wheel(0, 72), PartPose.offset(-11, 5, 13));
        root.addOrReplaceChild("wheel_front_right", wheel(40, 72), PartPose.offset(11, 5, 13));
        root.addOrReplaceChild("wheel_rear_left", wheel(80, 72), PartPose.offset(-11, 5, -14));
        root.addOrReplaceChild("wheel_rear_right", wheel(120, 72), PartPose.offset(11, 5, -14));
        return root.bake(256, 128);
    }

    private static CubeListBuilder wheel(int textureU, int textureV) {
        return CubeListBuilder.create().texOffs(textureU, textureV).addBox(-3, -5, -5, 6, 10, 10);
    }

    @Override
    public void setupAnim(CarRenderState state) {
        root().resetPose();
        root().getChild("wheel_front_left").yRot = state.steeringAngle;
        root().getChild("wheel_front_right").yRot = state.steeringAngle;
        root().getChild("wheel_front_left").xRot = state.wheelRotations[0];
        root().getChild("wheel_front_right").xRot = state.wheelRotations[1];
        root().getChild("wheel_rear_left").xRot = state.wheelRotations[2];
        root().getChild("wheel_rear_right").xRot = state.wheelRotations[3];
    }
}
