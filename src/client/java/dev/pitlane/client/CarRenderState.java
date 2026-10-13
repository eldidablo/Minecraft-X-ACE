package dev.pitlane.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;

public final class CarRenderState extends EntityRenderState {
    public final Quaternionf orientation = new Quaternionf();
    public final float[] wheelRotations = new float[4];
    public float steeringAngle;
    public String definitionPath = "front_drive";
}
