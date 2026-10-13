package dev.pitlane.physics;

import dev.pitlane.car.CarDefinition;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;

public record VehicleState(
        Vector3d position,
        Quaterniond orientation,
        Vector3d velocity,
        Vector3d angularVelocity,
        double engineRpm,
        int gear,
        double engineThrottle,
        double shiftTimerSeconds,
        List<WheelState> wheels
) {
    public VehicleState {
        position = new Vector3d(Objects.requireNonNull(position, "position"));
        orientation = new Quaterniond(Objects.requireNonNull(orientation, "orientation")).normalize();
        velocity = new Vector3d(Objects.requireNonNull(velocity, "velocity"));
        angularVelocity = new Vector3d(Objects.requireNonNull(angularVelocity, "angularVelocity"));
        wheels = List.copyOf(wheels);
        if (wheels.size() != 4) throw new IllegalArgumentException("A car state must contain exactly four wheels");
        if (!finite(position) || !finite(orientation) || !finite(velocity) || !finite(angularVelocity)
                || !Double.isFinite(engineRpm) || !Double.isFinite(engineThrottle) || !Double.isFinite(shiftTimerSeconds)) {
            throw new IllegalArgumentException("Vehicle state contains a non-finite value");
        }
        engineRpm = Math.max(0.0, engineRpm);
        engineThrottle = Math.max(0.0, Math.min(1.0, engineThrottle));
        shiftTimerSeconds = Math.max(0.0, shiftTimerSeconds);
    }

    @Override public Vector3d position() { return new Vector3d(position); }
    @Override public Quaterniond orientation() { return new Quaterniond(orientation); }
    @Override public Vector3d velocity() { return new Vector3d(velocity); }
    @Override public Vector3d angularVelocity() { return new Vector3d(angularVelocity); }

    public static VehicleState initial(Vec3 position, float yawRadians, CarDefinition definition) {
        List<WheelState> wheels = List.of(WheelState.airborne(), WheelState.airborne(), WheelState.airborne(), WheelState.airborne());
        return new VehicleState(
                new Vector3d(position.x, position.y, position.z),
                new Quaterniond().rotateY(yawRadians),
                new Vector3d(), new Vector3d(), definition.engine().idleRpm(), 1, 0.0, 0.0, wheels
        );
    }

    private static boolean finite(Vector3d vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static boolean finite(Quaterniond quaternion) {
        return Double.isFinite(quaternion.x) && Double.isFinite(quaternion.y)
                && Double.isFinite(quaternion.z) && Double.isFinite(quaternion.w);
    }
}
