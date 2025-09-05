package com.lycoris.awakening.entity;

import com.lycoris.awakening.LycorisAwakening;
import com.lycoris.awakening.entity.custom.projectile.SanguineProjectileEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final EntityType<SanguineProjectileEntity> SANGUINE_PROJECTILE =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    Identifier.of(LycorisAwakening.MOD_ID, "sanguine_projectile"),
                    EntityType.Builder.<SanguineProjectileEntity>create(SanguineProjectileEntity::new, SpawnGroup.MISC)
                            .dimensions(3f, 0.5f)            // hitbox size
                            .maxTrackingRange(64)                          // replaces trackRangeBlocks
                            .trackingTickInterval(10)                      // replaces trackedUpdateRate
                            .build("sanguine_projectile")
            );

    public static void init() {
        // Call this in your ModInitializer (onInitialize)
    }
}
