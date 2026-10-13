package dev.pitlane.physics;

import dev.pitlane.car.CarDefinition.CurvePoint;
import dev.pitlane.car.CarDefinition.EngineConfig;

public final class EngineSolver {
    private EngineSolver() { }

    public static EngineOutput evaluate(EngineConfig config, double rpm, double previousThrottle,
                                        CarControl input, double dt) {
        if (!Double.isFinite(dt) || dt <= 0.0) return new EngineOutput(config.idleRpm(), 0.0, 0.0);
        double targetThrottle = clamp(input.throttle(), 0.0, 1.0);
        double response = 1.0 - Math.exp(-config.throttleResponsePerSecond() * dt);
        double throttle = clamp(previousThrottle + (targetThrottle - previousThrottle) * response, 0.0, 1.0);
        double safeRpm = clamp(Double.isFinite(rpm) ? rpm : config.idleRpm(), config.idleRpm(), config.redlineRpm());
        double torque = torqueAt(config, safeRpm) * throttle - config.coastTorqueNm() * (1.0 - throttle);
        if (!Double.isFinite(torque)) torque = 0.0;
        return new EngineOutput(safeRpm, torque, throttle);
    }

    public static double torqueAt(EngineConfig config, double rpm) {
        var curve = config.torqueCurve();
        if (rpm <= curve.getFirst().rpm()) return curve.getFirst().torqueNm();
        for (int index = 1; index < curve.size(); index++) {
            CurvePoint upper = curve.get(index);
            if (rpm <= upper.rpm()) {
                CurvePoint lower = curve.get(index - 1);
                double amount = (rpm - lower.rpm()) / (upper.rpm() - lower.rpm());
                return lower.torqueNm() + (upper.torqueNm() - lower.torqueNm()) * amount;
            }
        }
        return curve.getLast().torqueNm();
    }

    public static double freeRevTarget(EngineConfig config, double throttle) {
        return config.idleRpm() + clamp(throttle, 0.0, 1.0) * (config.redlineRpm() - config.idleRpm()) * 0.78;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record EngineOutput(double rpm, double torqueNm, double throttle) { }
}
