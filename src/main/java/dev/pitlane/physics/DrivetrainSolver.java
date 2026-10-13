package dev.pitlane.physics;

import dev.pitlane.car.CarDefinition.DriveType;
import dev.pitlane.car.CarDefinition.DrivetrainConfig;
import dev.pitlane.car.CarDefinition.EngineConfig;
import dev.pitlane.car.CarDefinition.TransmissionConfig;

import java.util.Arrays;

public final class DrivetrainSolver {
    private static final double DRIVETRAIN_EFFICIENCY = 0.91;

    private DrivetrainSolver() { }

    public static ShiftState selectAutomaticGear(TransmissionConfig transmission, int currentGear,
                                                 double engineRpm, double shiftTimerSeconds,
                                                 double throttle, double dt) {
        double timer = Math.max(0.0, shiftTimerSeconds - Math.max(0.0, dt));
        int gear = Math.max(1, Math.min(transmission.gearRatios().size(), currentGear));
        if (timer <= 0.0) {
            if (engineRpm >= transmission.upshiftRpm() && gear < transmission.gearRatios().size()) {
                gear++;
                timer = transmission.shiftDurationSeconds();
            } else if (engineRpm <= transmission.downshiftRpm() && throttle > 0.28 && gear > 1) {
                gear--;
                timer = transmission.shiftDurationSeconds();
            }
        }
        return new ShiftState(gear, timer);
    }

    public static double coupledEngineRpm(TransmissionConfig transmission, int gear, double[] wheelAngularVelocity) {
        if (gear < 1 || transmission.gearRatios().isEmpty()) return 0.0;
        double drivenAverage = Arrays.stream(wheelAngularVelocity).filter(Double::isFinite).map(Math::abs).average().orElse(0.0);
        double ratio = transmission.gearRatios().get(Math.min(gear - 1, transmission.gearRatios().size() - 1))
                * transmission.finalDriveRatio();
        return drivenAverage * ratio * 60.0 / (Math.PI * 2.0);
    }

    public static double[] distribute(DrivetrainConfig config, TransmissionConfig transmission,
                                      EngineSolver.EngineOutput engine, int gear,
                                      double[] wheelAngularVelocity, boolean shifting) {
        if (wheelAngularVelocity.length != 4) throw new IllegalArgumentException("Expected four wheel speeds");
        double[] torques = new double[4];
        if (gear == 0 || gear < -1 || gear > transmission.gearRatios().size()) return torques;
        double ratio = gear < 0 ? -transmission.reverseRatio() : transmission.gearRatios().get(gear - 1);
        double shiftFactor = shifting ? 0.18 : 1.0;
        double axleTorque = engine.torqueNm() * ratio * transmission.finalDriveRatio() * DRIVETRAIN_EFFICIENCY * shiftFactor;

        double frontShare = switch (config.driveType()) {
            case FWD -> 1.0;
            case RWD -> 0.0;
            case AWD -> config.frontTorqueShare();
        };
        if (config.driveType() == DriveType.AWD) {
            double frontSpeed = (safeAbs(wheelAngularVelocity[0]) + safeAbs(wheelAngularVelocity[1])) * 0.5;
            double rearSpeed = (safeAbs(wheelAngularVelocity[2]) + safeAbs(wheelAngularVelocity[3])) * 0.5;
            double speedDifference = clamp((rearSpeed - frontSpeed) / Math.max(8.0, frontSpeed + rearSpeed), -0.35, 0.35);
            frontShare = clamp(frontShare + speedDifference * config.centerDifferentialLock(), 0.05, 0.95);
        }

        splitAxle(torques, 0, axleTorque * frontShare, wheelAngularVelocity,
                config.frontDifferentialLock());
        splitAxle(torques, 2, axleTorque * (1.0 - frontShare), wheelAngularVelocity,
                config.rearDifferentialLock());
        return torques;
    }

    private static void splitAxle(double[] output, int leftIndex, double axleTorque,
                                  double[] wheelAngularVelocity, double lock) {
        int rightIndex = leftIndex + 1;
        double leftSpeed = safeAbs(wheelAngularVelocity[leftIndex]);
        double rightSpeed = safeAbs(wheelAngularVelocity[rightIndex]);
        double speedDifference = clamp((rightSpeed - leftSpeed) / Math.max(5.0, leftSpeed + rightSpeed), -0.45, 0.45);
        double transfer = axleTorque * speedDifference * lock;
        output[leftIndex] = axleTorque * 0.5 + transfer;
        output[rightIndex] = axleTorque * 0.5 - transfer;
    }

    private static double safeAbs(double value) {
        return Double.isFinite(value) ? Math.abs(value) : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record ShiftState(int gear, double timerSeconds) { }
}
