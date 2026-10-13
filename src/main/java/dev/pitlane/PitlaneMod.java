package dev.pitlane;

import dev.pitlane.car.CarDefinitionReloadListener;
import dev.pitlane.entity.ModEntities;
import dev.pitlane.item.ModItems;
import dev.pitlane.network.CarNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PitlaneMod implements ModInitializer {
    public static final String MOD_ID = "pitlane";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.register();
        ModItems.register();
        CarNetworking.register();
        CarDefinitionReloadListener.register();
        LOGGER.info("Pitlane initialized");
    }
}
