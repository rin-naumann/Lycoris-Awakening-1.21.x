package com.lycoris.awakening.sound;

import com.lycoris.awakening.LycorisAwakening;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    public static final SoundEvent SLASH = registerSoundEvent("slash");
    public static final SoundEvent SANGUINE_EXPLOSION = registerSoundEvent("sanguine_explosion");

    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = Identifier.of(LycorisAwakening.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerSounds() {
        LycorisAwakening.LOGGER.info("Registering Mod Sounds for " + LycorisAwakening.MOD_ID);
    }
}
