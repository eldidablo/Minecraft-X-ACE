package dev.pitlane.physics;

import dev.pitlane.car.CarDefinition.SuspensionConfig;
import dev.pitlane.car.CarDefinition.WheelLayout;

public final class SuspensionSolver {
    private SuspensionSolver() { }

    public static SuspensionOutput evaluate(SuspensionConfig config, WheelLayout layout, WheelState previous,
                                             SurfaceQuery.WheelContact contact, int wheel, double dt) {
        if (!contact.touching()) {
            return new SuspensionOutput(-layout.droopStopTravelM(), 0.0, 0.0);
        }

        double radius = layout.positions().get(wheel).radiusM();
        double suspensionLength = Math.max(0.0, contact.distanceM() - radius);
        double restLength = layout.suspensionTravelM() * 0.55;
        double compression = clamp(restLength - suspensionLength, -layout.droopStopTravelM(), layout.suspensionTravelM());
        double compressionRate = dt > 0.0 ? (compression - previous.suspensionCompressionM()) / dt : 0.0;
        double springRate = wheel < 2 ? config.frontSpringNPerM() : config.rearSpringNPerM();
        double damperRate = wheel < 2
                ? (compressionRate >= 0.0 ? config.frontCompressionNsPerM() : config.frontReboundNsPerM())
                : (compressionRate >= 0.0 ? config.rearCompressionNsPerM() : config.rearReboundNsPerM());
        double load = Math.max(0.0, springRate * Math.max(0.0, compression) + damperRate * compressionRate);

        if (compression > layout.bumpStopTravelM()) {
            load += config.bumpStopNPerM() * (compression - layout.bumpStopTravelM());
        }
        if (compression < -layout.droopStopTravelM() + 1.0e-6) {
            load += config.droopStopNPerM() * (-layout.droopStopTravelM() - compression);
        }
        if (!Double.isFinite(load)) load = 0.0;
        return new SuspensionOutput(compression, compressionRate, Math.max(0.0, load));
    }

    public static double antiRollLoad(SuspensionConfig config, int wheel, double leftCompression, double rightCompression) {
        double stiffness = wheel < 2 ? config.frontAntiRollNPerM() : config.rearAntiRollNPerM();
        double transfer = (leftCompression - rightCompression) * stiffness * 0.5;
        return (wheel == 0 || wheel == 2) ? transfer : -transfer;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record SuspensionOutput(double compressionM, double velocityMps, double normalLoadN) { }
}
