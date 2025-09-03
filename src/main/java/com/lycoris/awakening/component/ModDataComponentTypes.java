package com.lycoris.awakening.component;

import com.lycoris.awakening.LycorisAwakening;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;

import java.util.function.UnaryOperator;

public class ModDataComponentTypes {

    //COOLDOWN COMPONENTS
    public static final ComponentType<Integer> SKILL_COOLDOWN = register(
            "skill_cooldown",
            builder -> builder.codec(Codecs.NONNEGATIVE_INT)
    );
    public static final ComponentType<Integer> ULTIMATE_COOLDOWN = register(
            "ultimate_cooldown",
            builder -> builder.codec(Codecs.NONNEGATIVE_INT)
    );
    public static final ComponentType<Integer> ABILITY_DURATION = register(
            "ability_duration",
            builder -> builder.codec(Codecs.NONNEGATIVE_INT)
    );


    //SPECIAL WEAPON COMPONENTS
    public static final ComponentType<Integer> SANGUINE_CHARGES = register(
            "sanguine_charges",
            builder -> builder.codec(Codecs.NONNEGATIVE_INT)
    );
    public static final ComponentType<Boolean> REAPER_STATE = register(
            "reaper_state",
            builder -> builder.codec(Codec.BOOL)
    );

    private static <T>ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(LycorisAwakening.MOD_ID, name),
                builderOperator.apply(ComponentType.builder()).build());
    }

    public static void registerDataComponentTypes() {
        LycorisAwakening.LOGGER.info("Registering Data Component Types for " + LycorisAwakening.MOD_ID);
    }
}
