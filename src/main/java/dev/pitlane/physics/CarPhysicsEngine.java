package dev.pitlane.physics;

import dev.pitlane.car.CarDefinition;
import dev.pitlane.car.CarDefinition.ChassisConfig;
import dev.pitlane.car.CarDefinition.WheelConfig;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

public final class CarPhysicsEngine {
    private static final double GRAVITY = 9.81;
    private static final double AIR_DENSITY = 1.225;
    private static final double WHEEL_INERTIA = 1.35;
    private static final double MAX_STEER_ANGLE_RAD = 0.54;

    private CarPhysicsEngine() { }

    public static VehicleState step(CarDefinition car, VehicleState state, CarControl input,
                                    SurfaceQuery surface, double dt) {
        if (!Double.isFinite(dt) || dt <= 0.0 || dt > 0.05) return state;

        ChassisConfig chassis = car.chassis();
        Quaterniond orientation = state.orientation();
        Vector3d bodyOrigin = state.position();
        Vector3d comOffset = new Vector3d(chassis.centerOfMassX(), chassis.centerOfMassY(), chassis.centerOfMassZ());
        Vector3d comWorld = new Vector3d(comOffset);
        orientation.transform(comWorld).add(bodyOrigin);
        Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        orientation.transform(up).normalize();
        Vector3d down = new Vector3d(up).negate();
        Vector3d worldForward = new Vector3d(0.0, 0.0, 1.0);
        orientation.transform(worldForward).normalize();

        List<WheelConfig> wheelConfigs = car.wheels().positions();
        SurfaceQuery.WheelContact[] contacts = new SurfaceQuery.WheelContact[4];
        Vector3d[] wheelOrigins = new Vector3d[4];
        double[] wheelSpeeds = new double[4];
        for (int index = 0; index < 4; index++) {
            WheelConfig wheel = wheelConfigs.get(index);
            Vector3d local = new Vector3d(wheel.xM(), wheel.yM(), wheel.zM());
            Vector3d offset = new Vector3d(local);
            orientation.transform(offset);
            wheelOrigins[index] = new Vector3d(bodyOrigin).add(offset);
            contacts[index] = surface.castWheel(index, new Vector3d(wheelOrigins[index]), new Vector3d(down),
                    wheel.radiusM() + car.wheels().suspensionTravelM() + car.wheels().droopStopTravelM());
            if (!contacts[index].regionAvailable()) return state;
            wheelSpeeds[index] = state.wheels().get(index).angularVelocityRadPerSec();
        }

        double meanDrivenSpeed = java.util.Arrays.stream(wheelSpeeds).filter(Double::isFinite).map(Math::abs).average().orElse(0.0);
        double coupledRpm = DrivetrainSolver.coupledEngineRpm(car.transmission(), state.gear(), wheelSpeeds);
        double freeRpm = EngineSolver.freeRevTarget(car.engine(), input.throttle());
        double coupling = clamp((meanDrivenSpeed - 2.0) / 10.0, 0.0, 0.82);
        double targetRpm = clamp(freeRpm * (1.0 - coupling) + Math.max(car.engine().idleRpm(), coupledRpm) * coupling,
                car.engine().idleRpm(), car.engine().redlineRpm());
        double engineRpm = approach(state.engineRpm(), targetRpm, 2.6, dt);
        EngineSolver.EngineOutput engine = EngineSolver.evaluate(car.engine(), engineRpm, state.engineThrottle(), input, dt);
        DrivetrainSolver.ShiftState shift = DrivetrainSolver.selectAutomaticGear(car.transmission(), state.gear(),
                engine.rpm(), state.shiftTimerSeconds(), engine.throttle(), dt);
        double[] driveTorque = DrivetrainSolver.distribute(car.drivetrain(), car.transmission(), engine,
                shift.gear(), wheelSpeeds, shift.timerSeconds() > 0.0);

        SuspensionSolver.SuspensionOutput[] suspension = new SuspensionSolver.SuspensionOutput[4];
        for (int index = 0; index < 4; index++) {
            suspension[index] = SuspensionSolver.evaluate(car.suspension(), car.wheels(), state.wheels().get(index),
                    contacts[index], index, dt);
        }
        for (int axle = 0; axle < 2; axle++) {
            int left = axle * 2;
            int right = left + 1;
            double leftComp = suspension[left].compressionM();
            double rightComp = suspension[right].compressionM();
            suspension[left] = withLoad(suspension[left], suspension[left].normalLoadN()
                    + SuspensionSolver.antiRollLoad(car.suspension(), left, leftComp, rightComp));
            suspension[right] = withLoad(suspension[right], suspension[right].normalLoadN()
                    + SuspensionSolver.antiRollLoad(car.suspension(), right, leftComp, rightComp));
        }

        Vector3d totalForce = new Vector3d(0.0, -chassis.massKg() * GRAVITY, 0.0);
        Vector3d totalTorque = new Vector3d();
        Vector3d velocity = state.velocity();
        Vector3d angularVelocity = state.angularVelocity();
        double speed = velocity.length();
        if (speed > 1.0e-6) {
            double drag = 0.5 * AIR_DENSITY * chassis.dragCoefficient() * chassis.frontalAreaM2() * speed * speed;
            totalForce.fma(-drag / speed, velocity);
        }

        List<WheelState> nextWheels = new ArrayList<>(4);
        double speedFade = clamp(1.0 - speed / 55.0, 0.48, 1.0);
        for (int index = 0; index < 4; index++) {
            WheelConfig wheelConfig = wheelConfigs.get(index);
            WheelState previous = state.wheels().get(index);
            SurfaceQuery.WheelContact contact = contacts[index];
            SuspensionSolver.SuspensionOutput suspensionOutput = suspension[index];
            double steerAngle = wheelConfig.steered() ? input.steering() * MAX_STEER_ANGLE_RAD * speedFade : 0.0;
            Vector3d normal = contact.touching() ? contact.normal() : up;
            Vector3d point = contact.touching() ? contact.point() : wheelOrigins[index];
            TireModel.TireForce tire = TireModel.TireForce.ZERO;
            Vector3d tireForward = steeredForward(orientation, steerAngle, normal);
            Vector3d tireRight = new Vector3d(normal).cross(tireForward).normalize();
            double longitudinalSpeed = 0.0;
            if (contact.touching() && suspensionOutput.normalLoadN() > 0.0) {
                Vector3d contactVelocity = new Vector3d(angularVelocity).cross(new Vector3d(point).sub(comWorld))
                        .add(velocity);
                longitudinalSpeed = contactVelocity.dot(tireForward);
                double lateralSpeed = contactVelocity.dot(tireRight);
                double wheelSurfaceSpeed = previous.angularVelocityRadPerSec() * wheelConfig.radiusM();
                double slipRatio = (wheelSurfaceSpeed - longitudinalSpeed)
                        / Math.max(1.0, Math.max(Math.abs(longitudinalSpeed), Math.abs(wheelSurfaceSpeed)));
                double slipAngle = Math.atan2(lateralSpeed, Math.max(1.0, Math.abs(longitudinalSpeed)));
                double grip = car.surfaceGrip().multiplier(contact.surfaceKey());
                tire = TireModel.evaluate(car.tire(), slipRatio, slipAngle, suspensionOutput.normalLoadN(), grip);

                Vector3d force = new Vector3d(normal).mul(suspensionOutput.normalLoadN())
                        .fma(tire.longitudinalN(), tireForward)
                        .fma(tire.lateralN(), tireRight);
                totalForce.add(force);
                totalTorque.add(new Vector3d(point).sub(comWorld).cross(force));
            }

            boolean frontAxle = index < 2;
            double serviceBrakeTorque = input.brake() * (frontAxle
                    ? car.brakes().frontMaxTorqueNm() : car.brakes().rearMaxTorqueNm()) * 0.5;
            double parkingBrakeTorque = input.handbrake() && wheelConfig.handbrakeAffected()
                    ? car.brakes().handbrakeMaxTorqueNm() * 0.5 : 0.0;
            double unbrakedTorque = driveTorque[index] - tire.longitudinalN() * wheelConfig.radiusM();
            double direction = Math.abs(previous.angularVelocityRadPerSec()) > 0.1
                    ? Math.signum(previous.angularVelocityRadPerSec()) : Math.signum(unbrakedTorque);
            double brakeTorque = serviceBrakeTorque + parkingBrakeTorque;
            double nextAngularVelocity = Math.abs(unbrakedTorque) <= brakeTorque
                    ? 0.0 : previous.angularVelocityRadPerSec() + (unbrakedTorque - direction * brakeTorque) / WHEEL_INERTIA * dt;
            if (!Double.isFinite(nextAngularVelocity)) nextAngularVelocity = 0.0;
            double rotation = wrapRadians(previous.rotationRad() + nextAngularVelocity * dt);
            nextWheels.add(new WheelState(nextAngularVelocity, suspensionOutput.compressionM(),
                    suspensionOutput.velocityMps(), suspensionOutput.normalLoadN(), contact.touching(), point,
                    normal, contact.surfaceKey(), tire.longitudinalSlip(), tire.lateralSlip(), steerAngle, rotation));
        }

        Vector3d localTorque = new Vector3d(totalTorque);
        new Quaterniond(orientation).conjugate().transform(localTorque);
        Vector3d localAngularAcceleration = new Vector3d(
                localTorque.x / chassis.rollInertiaKgM2(),
                localTorque.y / chassis.yawInertiaKgM2(),
                localTorque.z / chassis.pitchInertiaKgM2()
        );
        orientation.transform(localAngularAcceleration);
        Vector3d nextAngularVelocity = angularVelocity.fma(dt, localAngularAcceleration)
                .mul(Math.exp(-0.20 * dt));
        Quaterniond nextOrientation = integrateOrientation(orientation, nextAngularVelocity, dt);
        Vector3d nextVelocity = velocity.fma(dt / chassis.massKg(), totalForce).mul(Math.exp(-0.012 * dt));
        Vector3d nextComPosition = comWorld.fma(dt, nextVelocity);
        Vector3d rotatedComOffset = new Vector3d(comOffset);
        nextOrientation.transform(rotatedComOffset);
        Vector3d nextOrigin = nextComPosition.sub(rotatedComOffset);

        if (!finite(nextOrigin) || !finite(nextVelocity) || !finite(nextAngularVelocity)) return state;
        return new VehicleState(nextOrigin, nextOrientation, nextVelocity, nextAngularVelocity,
                engine.rpm(), shift.gear(), engine.throttle(), shift.timerSeconds(), nextWheels);
    }

    private static SuspensionSolver.SuspensionOutput withLoad(SuspensionSolver.SuspensionOutput output, double load) {
        return new SuspensionSolver.SuspensionOutput(output.compressionM(), output.velocityMps(), Math.max(0.0, load));
    }

    private static Vector3d steeredForward(Quaterniond orientation, double steeringAngle, Vector3d normal) {
        Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
        orientation.transform(forward);
        Vector3d right = new Vector3d(normal).cross(forward).normalize();
        forward.rotateAxis(steeringAngle, normal.x, normal.y, normal.z);
        forward.fma(-forward.dot(normal), normal).normalize();
        if (forward.lengthSquared() < 1.0e-8) return right;
        return forward;
    }

    private static Quaterniond integrateOrientation(Quaterniond orientation, Vector3d angularVelocity, double dt) {
        double speed = angularVelocity.length();
        if (speed < 1.0e-9) return orientation;
        Quaterniond delta = new Quaterniond().rotationAxis(speed * dt,
                angularVelocity.x / speed, angularVelocity.y / speed, angularVelocity.z / speed);
        return delta.mul(orientation).normalize();
    }

    private static double approach(double current, double target, double response, double dt) {
        return current + (target - current) * (1.0 - Math.exp(-response * dt));
    }

    private static double wrapRadians(double angle) {
        double wrapped = angle % (Math.PI * 2.0);
        return wrapped < 0.0 ? wrapped + Math.PI * 2.0 : wrapped;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
