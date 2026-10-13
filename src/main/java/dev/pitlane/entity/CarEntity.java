package dev.pitlane.entity;

import dev.pitlane.PitlaneMod;
import dev.pitlane.car.CarDefinition;
import dev.pitlane.car.CarDefinitionCatalog;
import dev.pitlane.physics.CarControl;
import dev.pitlane.physics.CarPhysicsEngine;
import dev.pitlane.physics.SurfaceQuery;
import dev.pitlane.physics.VehicleState;
import dev.pitlane.physics.WheelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public final class CarEntity extends Entity {
    private static final double SUBSTEP_SECONDS = 1.0 / 120.0;
    private static final int SUBSTEPS_PER_TICK = 6;
    private static final Identifier DEFAULT_DEFINITION = Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "front_drive");

    private static final EntityDataAccessor<String> DATA_DEFINITION_ID =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Quaternionfc> DATA_ORIENTATION =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> DATA_STEERING =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WHEEL_0 =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WHEEL_1 =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WHEEL_2 =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WHEEL_3 =
            SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);

    private Identifier definitionId = DEFAULT_DEFINITION;
    private VehicleState state;
    private CarControl driverInput = CarControl.NEUTRAL;
    private UUID driverId;
    private long lastAcceptedSequence = -1L;
    private int lastInputTick = Integer.MIN_VALUE;
    private final Quaternionf previousClientOrientation = new Quaternionf();
    private final Quaternionf currentClientOrientation = new Quaternionf();
    private final float[] previousClientWheelRotations = new float[4];
    private final float[] currentClientWheelRotations = new float[4];
    private float previousClientSteering;
    private float currentClientSteering;
    private boolean clientPresentationInitialized;

    public CarEntity(EntityType<? extends CarEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DEFINITION_ID, DEFAULT_DEFINITION.toString());
        builder.define(DATA_ORIENTATION, new Quaternionf());
        builder.define(DATA_STEERING, 0.0f);
        builder.define(DATA_WHEEL_0, 0.0f);
        builder.define(DATA_WHEEL_1, 0.0f);
        builder.define(DATA_WHEEL_2, 0.0f);
        builder.define(DATA_WHEEL_3, 0.0f);
    }

    public void initialize(Identifier definitionId, VehicleState initialState) {
        this.definitionId = definitionId;
        this.state = initialState;
        this.entityData.set(DATA_DEFINITION_ID, definitionId.toString());
        setEntityPositionFromState(initialState);
        syncPresentationState();
    }

    public Identifier definitionId() {
        Identifier synced = Identifier.tryParse(entityData.get(DATA_DEFINITION_ID));
        return synced == null ? definitionId : synced;
    }

    public VehicleState vehicleState() {
        return state;
    }

    public Quaterniond visualOrientation() {
        if (!level().isClientSide() && state != null) return state.orientation();
        Quaternionfc quaternion = entityData.get(DATA_ORIENTATION);
        return new Quaterniond(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w()).normalize();
    }

    public Quaternionf visualOrientation(float partialTick) {
        if (!level().isClientSide()) return new Quaternionf(visualOrientation());
        if (!clientPresentationInitialized) return new Quaternionf(entityData.get(DATA_ORIENTATION)).normalize();
        float alpha = clampPartialTick(partialTick);
        return new Quaternionf(previousClientOrientation).slerp(currentClientOrientation, alpha).normalize();
    }

    public float wheelRotation(int index) {
        if (!level().isClientSide() && state != null && index >= 0 && index < 4) {
            return (float) state.wheels().get(index).rotationRad();
        }
        return switch (index) {
            case 0 -> entityData.get(DATA_WHEEL_0);
            case 1 -> entityData.get(DATA_WHEEL_1);
            case 2 -> entityData.get(DATA_WHEEL_2);
            case 3 -> entityData.get(DATA_WHEEL_3);
            default -> 0.0f;
        };
    }

    public float wheelRotation(int index, float partialTick) {
        if (index < 0 || index >= 4) return 0.0f;
        if (!level().isClientSide()) return wheelRotation(index);
        if (!clientPresentationInitialized) return wheelRotation(index);
        float alpha = clampPartialTick(partialTick);
        float previous = previousClientWheelRotations[index];
        float current = currentClientWheelRotations[index];
        float difference = (float) Math.atan2(Math.sin(current - previous), Math.cos(current - previous));
        return previous + difference * alpha;
    }

    public float steeringAngle() {
        return !level().isClientSide() && state != null
                ? (float) state.wheels().getFirst().steerAngleRad()
                : entityData.get(DATA_STEERING);
    }

    public float steeringAngle(float partialTick) {
        if (!level().isClientSide()) return steeringAngle();
        if (!clientPresentationInitialized) return steeringAngle();
        float alpha = clampPartialTick(partialTick);
        return previousClientSteering + (currentClientSteering - previousClientSteering) * alpha;
    }

    private static float clampPartialTick(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 1.0f;
    }

    public boolean isDriver(Player player) {
        return getControllingPassenger() == player;
    }

    public boolean acceptDriverInput(Player player, CarControl input, long sequence) {
        if (!isDriver(player) || sequence < 0 || sequence <= lastAcceptedSequence) return false;
        lastAcceptedSequence = sequence;
        driverId = player.getUUID();
        driverInput = input;
        lastInputTick = tickCount;
        return true;
    }

    public void clearDriverInput(UUID playerId) {
        if (driverId != null && driverId.equals(playerId)) {
            driverInput = CarControl.NEUTRAL;
            driverId = null;
            lastAcceptedSequence = -1L;
            lastInputTick = Integer.MIN_VALUE;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (getPassengers().isEmpty() && !player.isPassenger()) {
            if (!level().isClientSide()) player.startRiding(this, true, true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger.getUUID().equals(driverId)) {
            driverInput = CarControl.NEUTRAL;
            driverId = null;
            lastAcceptedSequence = -1L;
            lastInputTick = Integer.MIN_VALUE;
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (passenger != getFirstPassenger()) return;
        Vector3d seat = new Vector3d(0.0, getBbHeight() * 0.23, -0.12);
        visualOrientation().transform(seat);
        moveFunction.accept(passenger, getX() + seat.x, getY() + getBbHeight() * 0.72 + seat.y,
                getZ() + seat.z);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            updateClientPresentation();
            return;
        }

        if (state == null) {
            CarDefinitionCatalog.find(definitionId()).ifPresent(definition -> {
                float yaw = (float) Math.toRadians(-getYRot());
                state = VehicleState.initial(new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ()), yaw, definition);
            });
        }
        if (state == null) return;

        CarDefinition definition = CarDefinitionCatalog.find(definitionId()).orElse(null);
        if (definition == null || !(level() instanceof ServerLevel serverLevel)) {
            driverInput = CarControl.NEUTRAL;
            return;
        }

        boolean hasDriver = getControllingPassenger() instanceof Player;
        CarControl input = hasDriver && lastInputTick != Integer.MIN_VALUE && tickCount - lastInputTick <= 1
                ? driverInput : CarControl.NEUTRAL;
        if (!hasDriver) {
            driverInput = CarControl.NEUTRAL;
            driverId = null;
            lastInputTick = Integer.MIN_VALUE;
        }

        VehicleState previous = state;
        for (int step = 0; step < SUBSTEPS_PER_TICK; step++) {
            state = CarPhysicsEngine.step(definition, state, input, new WorldSurfaceQuery(serverLevel), SUBSTEP_SECONDS);
        }

        Vec3 framePosition = toMinecraftPosition(state.position());
        if (!level().isLoaded(BlockPos.containing(framePosition.x, framePosition.y, framePosition.z))) {
            state = previous;
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 desiredFeet = toMinecraftFeet(state.position());
        Vec3 movement = desiredFeet.subtract(position());
        move(MoverType.SELF, movement);
        if (horizontalCollision || verticalCollision) {
            Vector3d correctedVelocity = state.velocity();
            if (horizontalCollision) {
                correctedVelocity.x = 0.0;
                correctedVelocity.z = 0.0;
            }
            if (verticalCollision) correctedVelocity.y = 0.0;
            state = new VehicleState(
                    new Vector3d(getX(), getY() + getBbHeight() * 0.5, getZ()), state.orientation(),
                    correctedVelocity, state.angularVelocity(), state.engineRpm(), state.gear(),
                    state.engineThrottle(), state.shiftTimerSeconds(), state.wheels()
            );
        } else {
            Vector3d actualOrigin = new Vector3d(getX(), getY() + getBbHeight() * 0.5, getZ());
            state = new VehicleState(actualOrigin, state.orientation(), state.velocity(), state.angularVelocity(),
                    state.engineRpm(), state.gear(), state.engineThrottle(), state.shiftTimerSeconds(), state.wheels());
        }

        setDeltaMovement(toMinecraftVector(state.velocity()));
        syncPresentationState();
    }

    private void updateClientPresentation() {
        Quaternionf syncedOrientation = new Quaternionf(entityData.get(DATA_ORIENTATION)).normalize();
        float[] syncedWheels = {
                entityData.get(DATA_WHEEL_0), entityData.get(DATA_WHEEL_1),
                entityData.get(DATA_WHEEL_2), entityData.get(DATA_WHEEL_3)
        };
        float syncedSteering = entityData.get(DATA_STEERING);
        if (!clientPresentationInitialized) {
            previousClientOrientation.set(syncedOrientation);
            currentClientOrientation.set(syncedOrientation);
            System.arraycopy(syncedWheels, 0, previousClientWheelRotations, 0, 4);
            System.arraycopy(syncedWheels, 0, currentClientWheelRotations, 0, 4);
            previousClientSteering = syncedSteering;
            currentClientSteering = syncedSteering;
            clientPresentationInitialized = true;
            return;
        }

        previousClientOrientation.set(currentClientOrientation);
        currentClientOrientation.set(syncedOrientation);
        System.arraycopy(currentClientWheelRotations, 0, previousClientWheelRotations, 0, 4);
        System.arraycopy(syncedWheels, 0, currentClientWheelRotations, 0, 4);
        previousClientSteering = currentClientSteering;
        currentClientSteering = syncedSteering;
    }

    private void syncPresentationState() {
        if (state == null || level().isClientSide()) return;
        Quaterniond orientation = state.orientation();
        entityData.set(DATA_DEFINITION_ID, definitionId.toString());
        entityData.set(DATA_ORIENTATION, new Quaternionf((float) orientation.x, (float) orientation.y,
                (float) orientation.z, (float) orientation.w));
        entityData.set(DATA_STEERING, (float) state.wheels().getFirst().steerAngleRad());
        entityData.set(DATA_WHEEL_0, (float) state.wheels().get(0).rotationRad());
        entityData.set(DATA_WHEEL_1, (float) state.wheels().get(1).rotationRad());
        entityData.set(DATA_WHEEL_2, (float) state.wheels().get(2).rotationRad());
        entityData.set(DATA_WHEEL_3, (float) state.wheels().get(3).rotationRad());
        Vector3d forward = new Vector3d(0.0, 0.0, 1.0);
        orientation.transform(forward);
        setYRot((float) Math.toDegrees(Math.atan2(-forward.x, forward.z)));
    }

    private void setEntityPositionFromState(VehicleState vehicleState) {
        Vector3d position = vehicleState.position();
        setPos(position.x, position.y - getBbHeight() * 0.5, position.z);
    }

    private Vec3 toMinecraftFeet(Vector3d position) {
        return new Vec3(position.x, position.y - getBbHeight() * 0.5, position.z);
    }

    private Vec3 toMinecraftPosition(Vector3d position) {
        return new Vec3(position.x, position.y, position.z);
    }

    private static Vec3 toMinecraftVector(Vector3d vector) {
        return new Vec3(vector.x, vector.y, vector.z);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString("PitlaneDefinition", definitionId().toString());
        if (state == null) return;
        Vector3d position = state.position();
        Quaterniond orientation = state.orientation();
        Vector3d velocity = state.velocity();
        Vector3d angular = state.angularVelocity();
        output.putDouble("FrameX", position.x);
        output.putDouble("FrameY", position.y);
        output.putDouble("FrameZ", position.z);
        output.putDouble("RotationX", orientation.x);
        output.putDouble("RotationY", orientation.y);
        output.putDouble("RotationZ", orientation.z);
        output.putDouble("RotationW", orientation.w);
        output.putDouble("VelocityX", velocity.x);
        output.putDouble("VelocityY", velocity.y);
        output.putDouble("VelocityZ", velocity.z);
        output.putDouble("AngularX", angular.x);
        output.putDouble("AngularY", angular.y);
        output.putDouble("AngularZ", angular.z);
        output.putDouble("EngineRpm", state.engineRpm());
        output.putInt("Gear", state.gear());
        output.putDouble("EngineThrottle", state.engineThrottle());
        output.putDouble("ShiftTimer", state.shiftTimerSeconds());
        for (int index = 0; index < 4; index++) {
            WheelState wheel = state.wheels().get(index);
            output.putDouble("WheelOmega" + index, wheel.angularVelocityRadPerSec());
            output.putDouble("WheelRotation" + index, wheel.rotationRad());
            output.putDouble("WheelCompression" + index, wheel.suspensionCompressionM());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Identifier loadedId = Identifier.tryParse(input.getStringOr("PitlaneDefinition", DEFAULT_DEFINITION.toString()));
        definitionId = loadedId == null ? DEFAULT_DEFINITION : loadedId;
        entityData.set(DATA_DEFINITION_ID, definitionId.toString());
        Vector3d position = new Vector3d(
                input.getDoubleOr("FrameX", getX()),
                input.getDoubleOr("FrameY", getY() + getBbHeight() * 0.5),
                input.getDoubleOr("FrameZ", getZ())
        );
        Quaterniond orientation = new Quaterniond(
                input.getDoubleOr("RotationX", 0.0), input.getDoubleOr("RotationY", 0.0),
                input.getDoubleOr("RotationZ", 0.0), input.getDoubleOr("RotationW", 1.0)
        ).normalize();
        Vector3d velocity = new Vector3d(input.getDoubleOr("VelocityX", 0.0), input.getDoubleOr("VelocityY", 0.0),
                input.getDoubleOr("VelocityZ", 0.0));
        Vector3d angular = new Vector3d(input.getDoubleOr("AngularX", 0.0), input.getDoubleOr("AngularY", 0.0),
                input.getDoubleOr("AngularZ", 0.0));
        List<WheelState> wheels = new java.util.ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            WheelState defaults = WheelState.airborne();
            wheels.add(new WheelState(input.getDoubleOr("WheelOmega" + index, 0.0),
                    input.getDoubleOr("WheelCompression" + index, defaults.suspensionCompressionM()), 0.0,
                    0.0, false, new Vector3d(), new Vector3d(0, 1, 0), "unknown", 0, 0, 0,
                    input.getDoubleOr("WheelRotation" + index, 0.0)));
        }
        state = new VehicleState(position, orientation, velocity, angular,
                input.getDoubleOr("EngineRpm", 900.0), input.getIntOr("Gear", 1),
                input.getDoubleOr("EngineThrottle", 0.0), input.getDoubleOr("ShiftTimer", 0.0), wheels);
        setEntityPositionFromState(state);
        syncPresentationState();
    }

    private final class WorldSurfaceQuery implements SurfaceQuery {
        private final ServerLevel level;

        private WorldSurfaceQuery(ServerLevel level) {
            this.level = level;
        }

        @Override
        public WheelContact castWheel(int wheel, Vector3d origin, Vector3d direction, double maxTravel) {
            Vector3d ray = new Vector3d(direction);
            if (!finite(ray) || ray.lengthSquared() < 1.0e-12 || !Double.isFinite(maxTravel) || maxTravel <= 0.0) {
                return WheelContact.miss();
            }
            ray.normalize();
            Vec3 start = new Vec3(origin.x, origin.y, origin.z);
            Vec3 end = start.add(ray.x * maxTravel, ray.y * maxTravel, ray.z * maxTravel);
            BlockPos startBlock = BlockPos.containing(start.x, start.y, start.z);
            BlockPos endBlock = BlockPos.containing(end.x, end.y, end.z);
            if (!level.isLoaded(startBlock) || !level.isLoaded(endBlock)) return WheelContact.unavailable();

            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CarEntity.this));
            if (hit.getType() == HitResult.Type.MISS || level.isOutsideBuildHeight(hit.getBlockPos())) return WheelContact.miss();
            BlockState block = level.getBlockState(hit.getBlockPos());
            Vec3 point = hit.getLocation();
            Direction face = hit.getDirection();
            Vector3d normal = new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ());
            double distance = start.distanceTo(point);
            return new WheelContact(true, true, new Vector3d(point.x, point.y, point.z), normal, distance, surfaceKey(block));
        }

        private String surfaceKey(BlockState block) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath();
            if (path.contains("asphalt")) return "asphalt";
            if (path.contains("ice")) return "ice";
            if (path.contains("snow")) return "snow";
            if (path.contains("gravel")) return "gravel";
            if (path.contains("sand")) return "sand";
            if (path.contains("grass") || path.contains("moss") || path.contains("leaves")) return "grass";
            if (path.contains("dirt") || path.contains("mud") || path.contains("clay")) return "dirt";
            if (path.contains("stone") || path.contains("deepslate") || path.contains("cobble")) return "stone";
            return "stone";
        }

        private boolean finite(Vector3d value) {
            return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
        }
    }
}
