package com.lycoris.awakening.mixin;

import com.lycoris.awakening.effect.ModEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class EnsanguinedAntiHealMixin {
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void healToDamage(float amount, CallbackInfo ci){
        LivingEntity entity = (LivingEntity) (Object)this;

        if(entity.hasStatusEffect(ModEffects.ENSANGUINED)){
            ci.cancel();
            entity.timeUntilRegen = 0;
            entity.damage(entity.getDamageSources().magic(), amount);
            entity.timeUntilRegen = 0;
        }
    }
}
