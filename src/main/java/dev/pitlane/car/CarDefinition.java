package dev.pitlane.car;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable SI configuration for one Pitlane vehicle. */
public record CarDefinition(
        Identifier id,
        String displayName,
        ChassisConfig chassis,
        WheelLayout wheels,
        SuspensionConfig suspension,
        TireConfig tire,
        EngineConfig engine,
        TransmissionConfig transmission,
        DrivetrainConfig drivetrain,
        BrakeConfig brakes,
        SurfaceGripConfig surfaceGrip
) {
    public CarDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(chassis, "chassis");
        Objects.requireNonNull(wheels, "wheels");
        Objects.requireNonNull(suspension, "suspension");
        Objects.requireNonNull(tire, "tire");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(transmission, "transmission");
        Objects.requireNonNull(drivetrain, "drivetrain");
        Objects.requireNonNull(brakes, "brakes");
        Objects.requireNonNull(surfaceGrip, "surfaceGrip");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        if (wheels.positions().size() != 4) throw new IllegalArgumentException("wheels.positions must contain exactly four wheels");
    }

    public static CarDefinition decode(Identifier id, JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("root must be a JSON object");
        JsonObject root = element.getAsJsonObject();

        JsonObject chassis = object(root, "chassis");
        JsonObject centerOfMass = object(chassis, "centerOfMassM");
        JsonObject inertia = object(chassis, "momentOfInertiaKgM2");
        ChassisConfig chassisConfig = new ChassisConfig(
                positive(number(chassis, "massKg"), "chassis.massKg"),
                positive(number(chassis, "lengthM"), "chassis.lengthM"),
                positive(number(chassis, "widthM"), "chassis.widthM"),
                positive(number(chassis, "heightM"), "chassis.heightM"),
                number(centerOfMass, "x"), number(centerOfMass, "y"), number(centerOfMass, "z"),
                positive(number(inertia, "pitch"), "momentOfInertia.pitch"),
                positive(number(inertia, "yaw"), "momentOfInertia.yaw"),
                positive(number(inertia, "roll"), "momentOfInertia.roll"),
                nonNegative(number(chassis, "dragCoefficient"), "chassis.dragCoefficient"),
                positive(number(chassis, "frontalAreaM2"), "chassis.frontalAreaM2")
        );

        JsonObject wheels = object(root, "wheels");
        List<WheelConfig> positions = new ArrayList<>();
        JsonArray wheelArray = array(wheels, "positions");
        for (int index = 0; index < wheelArray.size(); index++) {
            JsonElement wheelElement = wheelArray.get(index);
            if (!wheelElement.isJsonObject()) throw new IllegalArgumentException("wheels.positions[" + index + "] must be an object");
            JsonObject wheel = wheelElement.getAsJsonObject();
            positions.add(new WheelConfig(
                    number(wheel, "xM"), number(wheel, "yM"), number(wheel, "zM"),
                    positive(number(wheel, "radiusM"), "wheels.positions[" + index + "].radiusM"),
                    bool(wheel, "steered"), bool(wheel, "handbrakeAffected")
            ));
        }
        WheelLayout wheelLayout = new WheelLayout(
                positive(number(wheels, "wheelbaseM"), "wheels.wheelbaseM"),
                positive(number(wheels, "frontTrackM"), "wheels.frontTrackM"),
                positive(number(wheels, "rearTrackM"), "wheels.rearTrackM"),
                positions,
                positive(number(wheels, "suspensionTravelM"), "wheels.suspensionTravelM"),
                nonNegative(number(wheels, "bumpStopTravelM"), "wheels.bumpStopTravelM"),
                nonNegative(number(wheels, "droopStopTravelM"), "wheels.droopStopTravelM")
        );

        JsonObject suspension = object(root, "suspension");
        SuspensionConfig suspensionConfig = new SuspensionConfig(
                positive(number(suspension, "frontSpringNPerM"), "suspension.frontSpringNPerM"),
                positive(number(suspension, "rearSpringNPerM"), "suspension.rearSpringNPerM"),
                nonNegative(number(suspension, "frontCompressionNsPerM"), "suspension.frontCompressionNsPerM"),
                nonNegative(number(suspension, "frontReboundNsPerM"), "suspension.frontReboundNsPerM"),
                nonNegative(number(suspension, "rearCompressionNsPerM"), "suspension.rearCompressionNsPerM"),
                nonNegative(number(suspension, "rearReboundNsPerM"), "suspension.rearReboundNsPerM"),
                nonNegative(number(suspension, "frontAntiRollNPerM"), "suspension.frontAntiRollNPerM"),
                nonNegative(number(suspension, "rearAntiRollNPerM"), "suspension.rearAntiRollNPerM"),
                positive(number(suspension, "bumpStopNPerM"), "suspension.bumpStopNPerM"),
                positive(number(suspension, "droopStopNPerM"), "suspension.droopStopNPerM")
        );

        JsonObject tire = object(root, "tire");
        TireConfig tireConfig = new TireConfig(
                positive(number(tire, "longitudinalStiffness"), "tire.longitudinalStiffness"),
                positive(number(tire, "lateralStiffness"), "tire.lateralStiffness"),
                positive(number(tire, "peakSlipRatio"), "tire.peakSlipRatio"),
                positive(number(tire, "peakSlipAngleRad"), "tire.peakSlipAngleRad"),
                positive(number(tire, "peakFriction"), "tire.peakFriction"),
                nonNegative(number(tire, "loadSensitivity"), "tire.loadSensitivity"),
                positive(number(tire, "referenceLoadN"), "tire.referenceLoadN"),
                positive(number(tire, "combinedSlipExponent"), "tire.combinedSlipExponent"),
                positive(number(tire, "pressureKpa"), "tire.pressureKpa"),
                positive(number(tire, "idealPressureKpa"), "tire.idealPressureKpa"),
                number(tire, "temperatureC"),
                number(tire, "idealTemperatureC")
        );

        JsonObject engine = object(root, "engine");
        List<CurvePoint> torqueCurve = new ArrayList<>();
        JsonArray curveArray = array(engine, "torqueCurveNm");
        for (int index = 0; index < curveArray.size(); index++) {
            JsonElement pointElement = curveArray.get(index);
            if (!pointElement.isJsonObject()) throw new IllegalArgumentException("engine.torqueCurveNm[" + index + "] must be an object");
            JsonObject point = pointElement.getAsJsonObject();
            torqueCurve.add(new CurvePoint(
                    positive(number(point, "rpm"), "engine.torqueCurveNm.rpm"),
                    nonNegative(number(point, "torqueNm"), "engine.torqueCurveNm.torqueNm")
            ));
        }
        EngineConfig engineConfig = new EngineConfig(
                torqueCurve,
                positive(number(engine, "idleRpm"), "engine.idleRpm"),
                positive(number(engine, "redlineRpm"), "engine.redlineRpm"),
                positive(number(engine, "throttleResponsePerSecond"), "engine.throttleResponsePerSecond"),
                nonNegative(number(engine, "coastTorqueNm"), "engine.coastTorqueNm"),
                positive(number(engine, "inertiaKgM2"), "engine.inertiaKgM2")
        );

        JsonObject transmission = object(root, "transmission");
        List<Double> ratios = new ArrayList<>();
        JsonArray ratiosArray = array(transmission, "gearRatios");
        for (int index = 0; index < ratiosArray.size(); index++) {
            ratios.add(positive(finiteNumber(ratiosArray.get(index), "transmission.gearRatios[" + index + "]"), "transmission.gearRatios[" + index + "]"));
        }
        TransmissionConfig transmissionConfig = new TransmissionConfig(
                ratios,
                positive(number(transmission, "reverseRatio"), "transmission.reverseRatio"),
                positive(number(transmission, "finalDriveRatio"), "transmission.finalDriveRatio"),
                positive(number(transmission, "upshiftRpm"), "transmission.upshiftRpm"),
                positive(number(transmission, "downshiftRpm"), "transmission.downshiftRpm"),
                positive(number(transmission, "shiftDurationSeconds"), "transmission.shiftDurationSeconds")
        );

        JsonObject drivetrain = object(root, "drivetrain");
        DriveType driveType;
        try {
            driveType = DriveType.valueOf(string(drivetrain, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("drivetrain.type must be FWD, RWD or AWD", exception);
        }
        DrivetrainConfig drivetrainConfig = new DrivetrainConfig(
                driveType,
                fraction(number(drivetrain, "frontTorqueShare"), "drivetrain.frontTorqueShare"),
                fraction(number(drivetrain, "frontDifferentialLock"), "drivetrain.frontDifferentialLock"),
                fraction(number(drivetrain, "rearDifferentialLock"), "drivetrain.rearDifferentialLock"),
                fraction(number(drivetrain, "centerDifferentialLock"), "drivetrain.centerDifferentialLock")
        );

        JsonObject brakes = object(root, "brakes");
        BrakeConfig brakeConfig = new BrakeConfig(
                positive(number(brakes, "frontMaxTorqueNm"), "brakes.frontMaxTorqueNm"),
                positive(number(brakes, "rearMaxTorqueNm"), "brakes.rearMaxTorqueNm"),
                positive(number(brakes, "handbrakeMaxTorqueNm"), "brakes.handbrakeMaxTorqueNm"),
                fraction(number(brakes, "frontBias"), "brakes.frontBias")
        );

        JsonObject surfaceGrip = object(root, "surfaceGrip");
        JsonObject multiplierObject = object(surfaceGrip, "multipliers");
        Map<String, Double> multipliers = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : multiplierObject.entrySet()) {
            multipliers.put(entry.getKey(), nonNegative(finiteNumber(entry.getValue(), "surfaceGrip.multipliers." + entry.getKey()), "surfaceGrip.multipliers." + entry.getKey()));
        }
        double defaultGrip = positive(number(surfaceGrip, "default"), "surfaceGrip.default");

        if (engineConfig.torqueCurve().size() < 2) throw new IllegalArgumentException("engine.torqueCurveNm needs at least two points");
        for (int index = 1; index < engineConfig.torqueCurve().size(); index++) {
            if (engineConfig.torqueCurve().get(index).rpm() <= engineConfig.torqueCurve().get(index - 1).rpm()) {
                throw new IllegalArgumentException("engine.torqueCurveNm rpm values must be strictly increasing");
            }
        }
        if (engineConfig.idleRpm() >= engineConfig.redlineRpm()) throw new IllegalArgumentException("engine.idleRpm must be below redlineRpm");
        if (transmissionConfig.downshiftRpm() >= transmissionConfig.upshiftRpm()) throw new IllegalArgumentException("transmission.downshiftRpm must be below upshiftRpm");
        if (transmissionConfig.gearRatios().isEmpty()) throw new IllegalArgumentException("transmission.gearRatios must not be empty");
        if (driveType == DriveType.FWD && drivetrainConfig.frontTorqueShare() < 0.999) throw new IllegalArgumentException("FWD requires frontTorqueShare=1");
        if (driveType == DriveType.RWD && drivetrainConfig.frontTorqueShare() > 0.001) throw new IllegalArgumentException("RWD requires frontTorqueShare=0");
        if (driveType == DriveType.AWD && (drivetrainConfig.frontTorqueShare() <= 0 || drivetrainConfig.frontTorqueShare() >= 1)) {
            throw new IllegalArgumentException("AWD requires frontTorqueShare between 0 and 1");
        }

        return new CarDefinition(id, string(root, "displayName"), chassisConfig, wheelLayout, suspensionConfig,
                tireConfig, engineConfig, transmissionConfig, drivetrainConfig, brakeConfig,
                new SurfaceGripConfig(defaultGrip, multipliers));
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(name + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(name + " must be an array");
        return value.getAsJsonArray();
    }

    private static double number(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        return finiteNumber(value, name);
    }

    private static double finiteNumber(JsonElement element, String name) {
        try {
            double value = element.getAsDouble();
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a finite number", exception);
        }
    }

    private static String string(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static double positive(double value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be greater than zero");
        return value;
    }

    private static double nonNegative(double value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static double fraction(double value, String name) {
        if (value < 0 || value > 1) throw new IllegalArgumentException(name + " must be between zero and one");
        return value;
    }

    public enum DriveType { FWD, RWD, AWD }

    public record ChassisConfig(double massKg, double lengthM, double widthM, double heightM,
                                double centerOfMassX, double centerOfMassY, double centerOfMassZ,
                                double pitchInertiaKgM2, double yawInertiaKgM2, double rollInertiaKgM2,
                                double dragCoefficient, double frontalAreaM2) { }

    public record WheelConfig(double xM, double yM, double zM, double radiusM,
                              boolean steered, boolean handbrakeAffected) { }

    public record WheelLayout(double wheelbaseM, double frontTrackM, double rearTrackM,
                              List<WheelConfig> positions, double suspensionTravelM,
                              double bumpStopTravelM, double droopStopTravelM) {
        public WheelLayout { positions = List.copyOf(positions); }
    }

    public record SuspensionConfig(double frontSpringNPerM, double rearSpringNPerM,
                                   double frontCompressionNsPerM, double frontReboundNsPerM,
                                   double rearCompressionNsPerM, double rearReboundNsPerM,
                                   double frontAntiRollNPerM, double rearAntiRollNPerM,
                                   double bumpStopNPerM, double droopStopNPerM) { }

    public record TireConfig(double longitudinalStiffness, double lateralStiffness,
                             double peakSlipRatio, double peakSlipAngleRad, double peakFriction,
                             double loadSensitivity, double referenceLoadN, double combinedSlipExponent,
                             double pressureKpa, double idealPressureKpa,
                             double temperatureC, double idealTemperatureC) { }

    public record CurvePoint(double rpm, double torqueNm) { }

    public record EngineConfig(List<CurvePoint> torqueCurve, double idleRpm, double redlineRpm,
                               double throttleResponsePerSecond, double coastTorqueNm, double inertiaKgM2) {
        public EngineConfig { torqueCurve = List.copyOf(torqueCurve); }
    }

    public record TransmissionConfig(List<Double> gearRatios, double reverseRatio, double finalDriveRatio,
                                     double upshiftRpm, double downshiftRpm, double shiftDurationSeconds) {
        public TransmissionConfig { gearRatios = List.copyOf(gearRatios); }
    }

    public record DrivetrainConfig(DriveType driveType, double frontTorqueShare,
                                   double frontDifferentialLock, double rearDifferentialLock,
                                   double centerDifferentialLock) { }

    public record BrakeConfig(double frontMaxTorqueNm, double rearMaxTorqueNm,
                              double handbrakeMaxTorqueNm, double frontBias) { }

    public record SurfaceGripConfig(double defaultMultiplier, Map<String, Double> multipliers) {
        public SurfaceGripConfig { multipliers = Map.copyOf(multipliers); }

        public double multiplier(String surface) {
            return multipliers.getOrDefault(surface.toLowerCase(Locale.ROOT), defaultMultiplier);
        }
    }
}
