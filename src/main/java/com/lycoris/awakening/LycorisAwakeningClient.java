package com.lycoris.awakening;

import com.lycoris.awakening.client.WeatherClientHandler;
import com.lycoris.awakening.entity.ModEntities;
import com.lycoris.awakening.network.ModNetworking;
import com.lycoris.awakening.client.render.SanguineProjectileRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class LycorisAwakeningClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModNetworking.registerClientReceivers();
        EntityRendererRegistry.register(ModEntities.SANGUINE_PROJECTILE, SanguineProjectileRenderer::new);

        // Hook into the client tick loop
        ClientTickEvents.END_CLIENT_TICK.register(client -> WeatherClientHandler.clientTick());
    }
}
