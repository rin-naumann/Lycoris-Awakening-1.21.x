package com.lycoris.awakening;

import com.lycoris.awakening.client.WeatherClientHandler;
import com.lycoris.awakening.client.render.SanguineMoonRender;
import com.lycoris.awakening.network.ModNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class LycorisAwakeningClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModNetworking.registerClientReceivers();
        //SanguineMoonRender.init();
        // Hook into the client tick loop
        ClientTickEvents.END_CLIENT_TICK.register(client -> WeatherClientHandler.clientTick());
    }
}
