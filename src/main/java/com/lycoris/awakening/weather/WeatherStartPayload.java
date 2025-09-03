package com.lycoris.awakening.weather;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WeatherStartPayload(String weatherId, int ticks) implements CustomPayload {
    public static final Id<WeatherStartPayload> ID =
            new Id<>(Identifier.of("lycorisawakening", "weather_start"));

    // Codec for encoding/decoding
    public static final PacketCodec<RegistryByteBuf, WeatherStartPayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> { // encoder
                        buf.writeString(payload.weatherId);
                        buf.writeInt(payload.ticks);
                    },
                    buf -> new WeatherStartPayload( // decoder
                            buf.readString(32767),
                            buf.readInt()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
