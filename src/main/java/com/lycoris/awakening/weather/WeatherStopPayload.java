package com.lycoris.awakening.weather;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WeatherStopPayload() implements CustomPayload {
    public static final Id<WeatherStopPayload> ID =
            new Id<>(Identifier.of("lycorisawakening", "weather_stop"));

    public static final PacketCodec<RegistryByteBuf, WeatherStopPayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> {}, // no fields to encode
                    buf -> new WeatherStopPayload() // decode = empty
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
