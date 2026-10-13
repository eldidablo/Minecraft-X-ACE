package dev.pitlane.client;

import net.fabricmc.api.ClientModInitializer;
import dev.pitlane.PitlaneMod;
import dev.pitlane.entity.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class PitlaneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CarInputClient.register();
        EntityRenderers.register(ModEntities.CAR, CarRenderer::new);
        PitlaneMod.LOGGER.info("Pitlane client initialized");
    }
}
