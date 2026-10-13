package dev.pitlane.client;

import dev.pitlane.PitlaneMod;
import dev.pitlane.entity.CarEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

public final class CarRenderer extends EntityRenderer<CarEntity, CarRenderState> {
    private final ObjCarMesh model = ObjCarMesh.get();

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.8f;
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
            poseStack.pushPose();
            poseStack.translate(0.0, 0.02, 0.0);
            poseStack.mulPose(new Quaternionf(state.orientation));
            model.submit(poseStack, collector);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, cameraState);
    }
}
