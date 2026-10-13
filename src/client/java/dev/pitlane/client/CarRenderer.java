package dev.pitlane.client;

import dev.pitlane.PitlaneMod;
import dev.pitlane.entity.CarEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

public final class CarRenderer extends EntityRenderer<CarEntity, CarRenderState> {
    private static final Identifier FRONT_DRIVE_TEXTURE = texture("front_drive");
    private static final Identifier REAR_DRIVE_TEXTURE = texture("rear_drive");
    private static final Identifier ALL_WHEEL_DRIVE_TEXTURE = texture("all_wheel_drive");
    private final CarModel model = new CarModel();

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.8f;
    }

    private static Identifier texture(String path) {
        return Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "textures/entity/" + path + ".png");
    }

    @Override
    public CarRenderState createRenderState() {
        return new CarRenderState();
    }

    @Override
    public void extractRenderState(CarEntity entity, CarRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.orientation.set(entity.visualOrientation(partialTick));
        state.steeringAngle = entity.steeringAngle(partialTick);
        for (int wheel = 0; wheel < 4; wheel++) {
            state.wheelRotations[wheel] = entity.wheelRotation(wheel, partialTick);
        }
        state.definitionPath = entity.definitionId().getPath();
    }

    @Override
    public Vec3 getRenderOffset(CarRenderState state) {
        return Vec3.ZERO;
    }

    @Override
    public void submit(CarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.level.CameraRenderState cameraState) {
        if (!state.isInvisible) {
            model.setupAnim(state);
            poseStack.pushPose();
            poseStack.translate(0.0, 0.725, 0.0);
            poseStack.mulPose(new Quaternionf(state.orientation));
            poseStack.translate(0.0, -0.6, 0.0);
            collector.submitModel(model, state, poseStack, model.renderType(textureFor(state)), state.lightCoords,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor, null);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, cameraState);
    }

    private static Identifier textureFor(CarRenderState state) {
        return switch (state.definitionPath) {
            case "rear_drive" -> REAR_DRIVE_TEXTURE;
            case "all_wheel_drive" -> ALL_WHEEL_DRIVE_TEXTURE;
            default -> FRONT_DRIVE_TEXTURE;
        };
    }
}
