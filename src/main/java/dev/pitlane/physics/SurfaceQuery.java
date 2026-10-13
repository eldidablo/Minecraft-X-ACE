package dev.pitlane.physics;

import org.joml.Vector3d;

public interface SurfaceQuery {
    WheelContact castWheel(int wheel, Vector3d origin, Vector3d direction, double maxTravel);

    record WheelContact(boolean touching, boolean regionAvailable, Vector3d point, Vector3d normal,
                        double distanceM, String surfaceKey) {
        public WheelContact {
            point = new Vector3d(point);
            normal = new Vector3d(normal);
            surfaceKey = surfaceKey == null ? "unknown" : surfaceKey;
            if (!Double.isFinite(distanceM) || distanceM < 0.0) distanceM = Double.POSITIVE_INFINITY;
            if (!finite(point) || !finite(normal)) touching = false;
            if (normal.lengthSquared() < 1.0e-12) touching = false;
            else normal.normalize();
        }

        @Override public Vector3d point() { return new Vector3d(point); }
        @Override public Vector3d normal() { return new Vector3d(normal); }

        public static WheelContact miss() {
            return new WheelContact(false, true, new Vector3d(), new Vector3d(0.0, 1.0, 0.0), Double.POSITIVE_INFINITY, "unknown");
        }

        public static WheelContact unavailable() {
            return new WheelContact(false, false, new Vector3d(), new Vector3d(0.0, 1.0, 0.0), Double.POSITIVE_INFINITY, "unknown");
        }

        private static boolean finite(Vector3d vector) {
            return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
        }
    }
}
