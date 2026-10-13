package dev.pitlane.network;

import dev.pitlane.PitlaneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CarInputPayload(int entityId, long sequence, float throttle, float brake, float steering,
                              boolean handbrake) implements CustomPacketPayload {
    public static final Type<CarInputPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(PitlaneMod.MOD_ID, "car_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CarInputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CarInputPayload::entityId,
            ByteBufCodecs.VAR_LONG, CarInputPayload::sequence,
            ByteBufCodecs.FLOAT, CarInputPayload::throttle,
            ByteBufCodecs.FLOAT, CarInputPayload::brake,
            ByteBufCodecs.FLOAT, CarInputPayload::steering,
            ByteBufCodecs.BOOL, CarInputPayload::handbrake,
            CarInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean hasFiniteControls() {
        return Float.isFinite(throttle) && Float.isFinite(brake) && Float.isFinite(steering);
    }
}
