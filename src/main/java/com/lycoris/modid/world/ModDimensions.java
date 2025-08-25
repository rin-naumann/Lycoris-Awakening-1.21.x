package com.lycoris.modid.world;

import com.lycoris.modid.LycorisAwakening;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class ModDimensions {

    public static final RegistryKey<World> DOMAIN_WORLD = registerModDimension("domain");

    private static RegistryKey<World> registerModDimension(String name) {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(LycorisAwakening.MOD_ID, name));
    }

    public static void registerDimensions() {
        LycorisAwakening.LOGGER.info("Registering Mod Dimensions for " + LycorisAwakening.MOD_ID);
    }
}
