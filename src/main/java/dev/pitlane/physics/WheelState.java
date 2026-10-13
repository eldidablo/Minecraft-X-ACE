package dev.pitlane.physics;

import org.joml.Vector3d;

import java.util.Objects;

public record WheelState(
        double angularVelocityRadPerSec,
        double suspensionCompressionM,
        double suspensionVelocityMps,
        double normalLoadN,
        boolean grounded,
        Vector3d contactPoint,
        Vector3d contactNormal,
        String surfaceKey,
        double longitudinalSlip,
        double lateralSlip,
        double steerAngleRad,
        double rotationRad
) {
    public WheelState {
        contactPoint = new Vector3d(Objects.requireNonNull(contactPoint, "contactPoint"));
        contactNormal = new Vector3d(Objects.requireNonNull(contactNormal, "contactNormal"));
        surfaceKey = Objects.requireNonNull(surfaceKey, "surfaceKey");
    }

    @Override public Vector3d contactPoint() { return new Vector3d(contactPoint); }
    @Override public Vector3d contactNormal() { return new Vector3d(contactNormal); }

    public static WheelState airborne() {
        return new WheelState(0.0, 0.0, 0.0, 0.0, false,
                new Vector3d(), new Vector3d(0.0, 1.0, 0.0), "unknown", 0.0, 0.0, 0.0, 0.0);
    }
}
