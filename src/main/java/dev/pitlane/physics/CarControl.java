package dev.pitlane.physics;

public record CarControl(float throttle, float brake, float steering, boolean handbrake) {
    public static final CarControl NEUTRAL = new CarControl(0, 0, 0, false);

    public CarControl {
        throttle = clampFinite(throttle, 0.0f, 1.0f);
        brake = clampFinite(brake, 0.0f, 1.0f);
        steering = clampFinite(steering, -1.0f, 1.0f);
    }

    private static float clampFinite(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
