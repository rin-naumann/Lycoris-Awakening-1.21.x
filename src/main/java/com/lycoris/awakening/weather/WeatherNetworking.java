package com.lycoris.awakening.weather;

import com.lycoris.awakening.client.WeatherClientHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class WeatherNetworking {

    // Send packets from server → clients
    public static void sendWeatherStart(ServerWorld world, CustomWeatherType type, int ticks) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, new WeatherStartPayload(type.getId(), ticks));
        }
    }

    public static void sendWeatherStop(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send(player, new WeatherStopPayload());
        }
    }

    // Register handlers on server
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(WeatherStartPayload.ID, WeatherStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WeatherStopPayload.ID, WeatherStopPayload.CODEC);
    }

    // Register handlers on client
    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(WeatherStartPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        CustomWeatherType type = CustomWeatherType.fromId(payload.weatherId());
                        if (type != null) {
                            WeatherClientHandler.startWeather(type, payload.ticks());
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(WeatherStopPayload.ID,
                (payload, context) -> {
                    context.client().execute(WeatherClientHandler::stopWeather);
                });
    }
}
