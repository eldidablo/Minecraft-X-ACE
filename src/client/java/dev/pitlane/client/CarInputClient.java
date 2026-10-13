package dev.pitlane.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.pitlane.PitlaneMod;
import dev.pitlane.entity.CarEntity;
import dev.pitlane.network.CarInputPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CarInputClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "controls"));
    private static final KeyMapping ACCELERATE = register("key.pitlane.accelerate", GLFW.GLFW_KEY_W);
    private static final KeyMapping BRAKE = register("key.pitlane.brake", GLFW.GLFW_KEY_S);
    private static final KeyMapping STEER_LEFT = register("key.pitlane.steer_left", GLFW.GLFW_KEY_A);
    private static final KeyMapping STEER_RIGHT = register("key.pitlane.steer_right", GLFW.GLFW_KEY_D);
    private static final KeyMapping HANDBRAKE = register("key.pitlane.handbrake", GLFW.GLFW_KEY_SPACE);
    private static long nextSequence;

    private CarInputClient() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CarInputClient::tick);
    }

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, defaultKey, CATEGORY));
    }

    private static void tick(Minecraft client) {
        if (client.player == null || !(client.player.getVehicle() instanceof CarEntity car)) return;

        boolean focusedGameplay = client.screen == null;
        float throttle = focusedGameplay && ACCELERATE.isDown() ? 1.0f : 0.0f;
        float brake = focusedGameplay && BRAKE.isDown() ? 1.0f : 0.0f;
        float steering = focusedGameplay
                ? (STEER_LEFT.isDown() ? 1.0f : 0.0f) - (STEER_RIGHT.isDown() ? 1.0f : 0.0f)
                : 0.0f;
        boolean handbrake = focusedGameplay && HANDBRAKE.isDown();
        ClientPlayNetworking.send(new CarInputPayload(car.getId(), nextSequence++, throttle, brake, steering, handbrake));
    }
}
