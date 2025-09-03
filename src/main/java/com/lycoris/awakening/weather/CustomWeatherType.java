package com.lycoris.awakening.weather;

public enum CustomWeatherType {
    SANGUINE_MOON("sanguine_moon", 20 * 60 * 3),
    ASH_STORM("ash_storm", 20 * 60 * 2);

    private final String id;
    private final int durationTicks;

    CustomWeatherType(String id, int durationTicks) {
        this.id = id;
        this.durationTicks = durationTicks;
    }

    public String getId() { return id; }
    public int getDurationTicks() { return durationTicks; }

    public static CustomWeatherType fromId(String id) {
        for (CustomWeatherType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}