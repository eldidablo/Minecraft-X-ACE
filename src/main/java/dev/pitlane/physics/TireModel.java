package dev.pitlane.physics;

import dev.pitlane.car.CarDefinition.TireConfig;

public final class TireModel {
    private TireModel() { }

    public static TireForce evaluate(TireConfig config, double longitudinalSlip, double lateralSlip,
                                     double normalLoad, double surfaceGrip) {
        if (!Double.isFinite(normalLoad) || normalLoad <= 0.0 || !Double.isFinite(surfaceGrip) || surfaceGrip <= 0.0) {
            return TireForce.ZERO;
        }
        double safeLongitudinalSlip = finiteOrZero(longitudinalSlip);
        double safeLateralSlip = finiteOrZero(lateralSlip);
        double loadRatio = Math.max(0.05, normalLoad / config.referenceLoadN());
        double loadFactor = clamp(Math.pow(loadRatio, -config.loadSensitivity()), 0.62, 1.18);
        double pressureFactor = clamp(1.0 - Math.abs(config.pressureKpa() / config.idealPressureKpa() - 1.0) * 0.55, 0.70, 1.0);
        double temperatureFactor = clamp(1.0 - Math.abs(config.temperatureC() - config.idealTemperatureC()) / 145.0, 0.60, 1.0);
        double frictionLimit = normalLoad * config.peakFriction() * surfaceGrip * loadFactor * pressureFactor * temperatureFactor;

        double longitudinal = brushCurve(safeLongitudinalSlip, config.peakSlipRatio(), config.longitudinalStiffness());
        double lateral = -brushCurve(safeLateralSlip, config.peakSlipAngleRad(), config.lateralStiffness());
        double exponent = config.combinedSlipExponent();
        double combined = Math.pow(Math.pow(Math.abs(longitudinal), exponent) + Math.pow(Math.abs(lateral), exponent), 1.0 / exponent);
        double scale = combined > frictionLimit && combined > 0.0 ? frictionLimit / combined : 1.0;
        longitudinal *= scale;
        lateral *= scale;
        return new TireForce(finiteOrZero(longitudinal), finiteOrZero(lateral), safeLongitudinalSlip, safeLateralSlip,
                frictionLimit);
    }

    private static double brushCurve(double slip, double peakSlip, double stiffness) {
        double magnitude = Math.abs(slip);
        double sign = Math.signum(slip);
        double normalized = magnitude / peakSlip;
        if (normalized <= 1.0) {
            return sign * stiffness * magnitude * (2.0 - normalized);
        }
        double peakForce = stiffness * peakSlip;
        double falloff = 1.0 / (1.0 + 0.55 * (normalized - 1.0));
        return sign * peakForce * falloff;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record TireForce(double longitudinalN, double lateralN, double longitudinalSlip, double lateralSlip,
                            double frictionLimitN) {
        public static final TireForce ZERO = new TireForce(0.0, 0.0, 0.0, 0.0, 0.0);
    }
}
