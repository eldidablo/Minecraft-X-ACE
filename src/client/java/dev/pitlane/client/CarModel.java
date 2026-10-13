package dev.pitlane.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class CarModel extends EntityModel<CarRenderState> {
    public CarModel() {
        super(createRoot(), texture -> RenderTypes.entityCutout(texture));
    }

    private static net.minecraft.client.model.geom.ModelPart createRoot() {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-12, 6, -22, 24, 7, 44), PartPose.ZERO);
        root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 56)
                .addBox(-10, 11, 2, 20, 4, 18), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(144, 0)
                .addBox(-8, 12, -9, 16, 7, 18), PartPose.ZERO);
        root.addOrReplaceChild("front_bumper", CubeListBuilder.create().texOffs(144, 52)
                .addBox(-12, 6, 22, 24, 3, 2), PartPose.ZERO);
        root.addOrReplaceChild("rear_bumper", CubeListBuilder.create().texOffs(144, 60)
                .addBox(-12, 6, -24, 24, 3, 2), PartPose.ZERO);

        // Keep the visible wheels aligned with the wheel contact points used by the physics model.
        // Eight short, overlapping segments approximate a rounded tire instead of a solid cuboid.
        PartDefinition frontLeft = root.addOrReplaceChild("wheel_front_left", CubeListBuilder.create(),
                PartPose.offset(-12.3f, 3.5f, 20.5f));
        PartDefinition frontRight = root.addOrReplaceChild("wheel_front_right", CubeListBuilder.create(),
                PartPose.offset(12.3f, 3.5f, 20.5f));
        PartDefinition rearLeft = root.addOrReplaceChild("wheel_rear_left", CubeListBuilder.create(),
                PartPose.offset(-12.2f, 3.5f, -20.5f));
        PartDefinition rearRight = root.addOrReplaceChild("wheel_rear_right", CubeListBuilder.create(),
                PartPose.offset(12.2f, 3.5f, -20.5f));
        addWheelSegments(frontLeft, 0, 72);
        addWheelSegments(frontRight, 40, 72);
        addWheelSegments(rearLeft, 80, 72);
        addWheelSegments(rearRight, 120, 72);
        return root.bake(256, 128);
    }

    private static void addWheelSegments(PartDefinition wheel, int textureU, int textureV) {
        for (int segment = 0; segment < 8; segment++) {
            double angle = segment * Math.PI / 4.0;
            float y = (float) (3.5 * Math.cos(angle));
            float z = (float) (3.5 * Math.sin(angle));
            int u = textureU + (segment % 4) * 8;
            int v = textureV + (segment / 4) * 8;
            wheel.addOrReplaceChild("segment_" + segment,
                    CubeListBuilder.create().texOffs(u, v).addBox(-3, -2, -1.7f, 6, 4, 3.4f),
                    PartPose.offsetAndRotation(0, y, z, (float) angle, 0, 0));
        }
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
