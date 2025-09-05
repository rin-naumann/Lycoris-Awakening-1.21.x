package com.lycoris.awakening.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleEffect;

public class EnsanguinedEffect extends StatusEffect {
    protected EnsanguinedEffect(StatusEffectCategory category, int color, ParticleEffect particleEffect) {
        super(category, color, particleEffect);
    }

    @Override
    public void onApplied(LivingEntity entity, int amplifier) {
        entity.timeUntilRegen = 0;

        float damage = 2.0f * (amplifier * 2);
        entity.damage(entity.getDamageSources().magic(), damage);

        super.onApplied(entity, amplifier);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }
}
