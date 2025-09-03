package com.lycoris.awakening.network;

import com.lycoris.awakening.weather.WeatherNetworking;

public class ModNetworking {

    // Called on common init
    public static void registerPayloads() {
        WeatherNetworking.registerPayloads();
    }

    // Called on client init
    public static void registerClientReceivers() {
        WeatherNetworking.registerClientReceivers();
    }
}
