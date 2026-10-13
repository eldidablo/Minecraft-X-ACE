package dev.pitlane.network;

import dev.pitlane.entity.CarEntity;
import dev.pitlane.physics.CarControl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class CarNetworking {
    private CarNetworking() { }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(CarInputPayload.TYPE, CarInputPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CarInputPayload.TYPE, (payload, context) -> {
            if (!payload.hasFiniteControls() || payload.sequence() < 0) return;

            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (player.getVehicle() instanceof CarEntity car
                        && car.getId() == payload.entityId()
                        && car.level() == player.level()) {
                    car.acceptDriverInput(player,
                            new CarControl(payload.throttle(), payload.brake(), payload.steering(), payload.handbrake()),
                            payload.sequence());
                }
            });
        });
    }
}
