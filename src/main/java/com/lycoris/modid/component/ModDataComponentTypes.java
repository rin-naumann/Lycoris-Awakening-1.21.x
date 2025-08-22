package com.lycoris.modid.component;

import com.lycoris.modid.LycorisAwakening;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;

import java.util.function.UnaryOperator;

public class ModDataComponentTypes {

    public static final ComponentType<Integer> SANGUINE_CHARGES = register(
            "sainguine_charges",
            builder -> builder.codec(Codecs.NONNEGATIVE_INT)
    );

    private static <T>ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(LycorisAwakening.MOD_ID, name),
                builderOperator.apply(ComponentType.builder()).build());
    }

    public static void registerDataComponentTypes() {
        LycorisAwakening.LOGGER.info("Registering Data Component Types for " + LycorisAwakening.MOD_ID);
    }
}
