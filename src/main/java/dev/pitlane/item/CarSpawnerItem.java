package dev.pitlane.item;

import dev.pitlane.PitlaneMod;
import dev.pitlane.car.CarDefinition;
import dev.pitlane.car.CarDefinitionCatalog;
import dev.pitlane.entity.CarEntity;
import dev.pitlane.entity.ModEntities;
import dev.pitlane.physics.VehicleState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class CarSpawnerItem extends Item {
    private final Identifier definitionId;

    public CarSpawnerItem(Identifier definitionId, Properties properties) {
        super(properties);
        this.definitionId = definitionId;
    }

    @Override
    public InteractionResult use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        CarDefinition definition = CarDefinitionCatalog.find(definitionId).orElse(null);
        if (definition == null) {
            PitlaneMod.LOGGER.error("Cannot spawn car; definition {} is unavailable", definitionId);
            return InteractionResult.FAIL;
        }

        double yaw = Math.toRadians(player.getYRot());
        double spawnX = player.getX() - Math.sin(yaw) * 2.6;
        double spawnZ = player.getZ() + Math.cos(yaw) * 2.6;
        Vec3 bodyOrigin = new Vec3(spawnX, player.getY() + definition.chassis().heightM() * 0.5, spawnZ);
        CarEntity car = ModEntities.CAR.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (car == null) return InteractionResult.FAIL;
        VehicleState initialState = VehicleState.initial(bodyOrigin, (float) -yaw, definition);
        car.initialize(definitionId, initialState);
        car.setYRot(player.getYRot());
        if (!level.noCollision(car)) return InteractionResult.FAIL;
        if (!level.addFreshEntity(car)) return InteractionResult.FAIL;
        if (!player.isCreative()) player.getItemInHand(hand).shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return use(context.getLevel(), context.getPlayer(), context.getHand());
    }
}
