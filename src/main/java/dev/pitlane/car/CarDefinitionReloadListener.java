package dev.pitlane.car;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.pitlane.PitlaneMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CarDefinitionReloadListener {
    private static final String RESOURCE_DIRECTORY = "cars/";

    private CarDefinitionReloadListener() { }

    public static void register() {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) reload(resources);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> reload(server.getResourceManager()));
    }

    public static void reload(ResourceManager resources) {
        Map<Identifier, CarDefinition> loaded = new LinkedHashMap<>();
        resources.listResources("cars", id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            String path = resourceId.getPath();
            if (!path.startsWith(RESOURCE_DIRECTORY) || path.length() <= RESOURCE_DIRECTORY.length() + ".json".length()) {
                PitlaneMod.LOGGER.warn("Ignoring car definition outside cars/<id>.json: {}", resourceId);
                return;
            }
            String definitionPath = path.substring(RESOURCE_DIRECTORY.length(), path.length() - ".json".length());
            Identifier definitionId = Identifier.tryBuild(resourceId.getNamespace(), definitionPath);
            if (definitionId == null) {
                PitlaneMod.LOGGER.error("Invalid car definition identifier derived from {}", resourceId);
                return;
            }

            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                CarDefinition definition = CarDefinition.decode(definitionId, json);
                CarDefinition previous = loaded.putIfAbsent(definitionId, definition);
                if (previous != null) PitlaneMod.LOGGER.error("Duplicate car definition id {}; ignoring resource {}", definitionId, resourceId);
            } catch (Exception exception) {
                PitlaneMod.LOGGER.error("Rejected car definition {} from resource {}: {}", definitionId, resourceId, exception.getMessage());
            }
        });

        CarDefinitionCatalog.replaceAll(loaded);
        PitlaneMod.LOGGER.info("Loaded {} Pitlane car definitions", loaded.size());
    }
}
