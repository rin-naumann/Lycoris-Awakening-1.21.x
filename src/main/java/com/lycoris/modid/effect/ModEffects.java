package com.lycoris.modid.effect;

import com.lycoris.modid.LycorisAwakening;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class ModEffects {

    public static final RegistryEntry<StatusEffect> DEATH = registerStatusEffect("death",
            new DeathEffect(StatusEffectCategory.HARMFUL, 0x2E0854));


    private static RegistryEntry<StatusEffect> registerStatusEffect(String name, StatusEffect statusEffect){
        return Registry.registerReference(Registries.STATUS_EFFECT, Identifier.of(LycorisAwakening.MOD_ID, name), statusEffect);
    }

    public static void registerEffects() {
        LycorisAwakening.LOGGER.info("Registering Mod Effects for " + LycorisAwakening.MOD_ID);
    }
}
