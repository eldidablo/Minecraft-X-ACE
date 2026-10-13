package dev.pitlane.item;

import dev.pitlane.PitlaneMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
    public static final Item FRONT_DRIVE = registerSpawner("front_drive_car", "front_drive");
    public static final Item REAR_DRIVE = registerSpawner("rear_drive_car", "rear_drive");
    public static final Item ALL_WHEEL_DRIVE = registerSpawner("all_wheel_drive_car", "all_wheel_drive");
    public static final CreativeModeTab PITLANE_TAB = registerTab();

    private ModItems() { }

    public static void register() {
        PitlaneMod.LOGGER.info("Registered Pitlane car spawners and creative tab");
    }

    private static Item registerSpawner(String itemPath, String carPath) {
        Identifier id = Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, itemPath);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = new CarSpawnerItem(Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, carPath),
                new Item.Properties().setId(key).stacksTo(1));
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    private static CreativeModeTab registerTab() {
        Identifier id = Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "pitlane");
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id);
        CreativeModeTab tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 9)
                .title(Component.translatable("itemGroup.pitlane"))
                .icon(() -> new ItemStack(FRONT_DRIVE))
                .displayItems((parameters, output) -> {
                    output.accept(new ItemStack(FRONT_DRIVE));
                    output.accept(new ItemStack(REAR_DRIVE));
                    output.accept(new ItemStack(ALL_WHEEL_DRIVE));
                })
                .build();
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, tab);
    }
}
