package dev.pitlane.entity;

import dev.pitlane.PitlaneMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<CarEntity> CAR = register("car");

    private ModEntities() { }

    public static void register() {
        PitlaneMod.LOGGER.info("Registered Pitlane car entity type");
    }

    private static EntityType<CarEntity> register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, path);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        EntityType<CarEntity> type = EntityType.Builder.of(CarEntity::new, MobCategory.MISC)
                .sized(2.0f, 1.37f)
                .clientTrackingRange(96)
                .updateInterval(1)
                .build(key);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id, type);
    }
}
