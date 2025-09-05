package com.lycoris.awakening.effect;

import com.lycoris.awakening.LycorisAwakening;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public class ModEffects {

    public static final RegistryEntry<StatusEffect> DEATH = registerStatusEffect("death",
            new DeathEffect(StatusEffectCategory.HARMFUL, 0x2E0854));
    public static final RegistryEntry<StatusEffect> ENSANGUINED = registerStatusEffect("ensanguined",
            new EnsanguinedEffect(StatusEffectCategory.HARMFUL, 0x5E1919,
                    new DustParticleEffect(new Vector3f(94, 25, 25), 0.5f))
            );


    private static RegistryEntry<StatusEffect> registerStatusEffect(String name, StatusEffect statusEffect){
        return Registry.registerReference(Registries.STATUS_EFFECT, Identifier.of(LycorisAwakening.MOD_ID, name), statusEffect);
    }

    public static void registerEffects() {
        LycorisAwakening.LOGGER.info("Registering Mod Effects for " + LycorisAwakening.MOD_ID);
    }
}
