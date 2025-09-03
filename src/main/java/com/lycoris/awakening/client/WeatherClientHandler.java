package com.lycoris.awakening.client;

import com.lycoris.awakening.weather.CustomWeatherType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class WeatherClientHandler {
    private static CustomWeatherType activeWeather;
    private static int remainingTicks;

    public static void startWeather(CustomWeatherType type, int ticks) {
        activeWeather = type;
        remainingTicks = ticks;

        // Debug logs
        System.out.println("[LycorisAwakening] DEBUG: Started weather " + type.getId() + " for " + ticks + " ticks.");
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                Text.of("§e[DEBUG] Weather started: " + type.getId() + " (" + ticks + " ticks)")
        );
    }

    public static void stopWeather() {
        if (activeWeather != null) {
            System.out.println("[LycorisAwakening] DEBUG: Stopped weather " + activeWeather.getId());
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                    Text.of("§e[DEBUG] Weather stopped: " + activeWeather.getId())
            );
        }
        activeWeather = null;
        remainingTicks = 0;
    }

    public static CustomWeatherType getActiveWeather() {
        return activeWeather;
    }

    // Example: use this in a sky renderer mixin
    public static boolean isSanguineMoon() {
        return activeWeather == CustomWeatherType.SANGUINE_MOON;
    }

    // Optional ticking to auto-stop
    public static void clientTick() {
        if (activeWeather != null && remainingTicks > 0) {
            remainingTicks--;
            if (remainingTicks == 0) {
                stopWeather();
            }
        }
    }
}
