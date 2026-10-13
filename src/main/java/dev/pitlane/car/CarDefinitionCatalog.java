package dev.pitlane.car;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CarDefinitionCatalog {
    private static final AtomicReference<Map<Identifier, CarDefinition>> DEFINITIONS =
            new AtomicReference<>(Map.of());

    private CarDefinitionCatalog() { }

    public static Optional<CarDefinition> find(Identifier id) {
        return Optional.ofNullable(DEFINITIONS.get().get(id));
    }

    public static Map<Identifier, CarDefinition> snapshot() {
        return DEFINITIONS.get();
    }

    static void replaceAll(Map<Identifier, CarDefinition> definitions) {
        DEFINITIONS.set(Map.copyOf(definitions));
    }
}
