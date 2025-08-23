package com.lycoris.modid.item.custom;

import com.lycoris.modid.component.ModDataComponentTypes;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class MementoMoriItem extends HoeItem {

    // == Reaper State Helpers ==
    public static boolean getReaperState(ItemStack stack){
        return Boolean.TRUE.equals(stack.get(ModDataComponentTypes.REAPER_STATE));
    }

    public static void setReaperState(ItemStack stack, boolean state) {
        stack.set(ModDataComponentTypes.REAPER_STATE, state);
    }

    public MementoMoriItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, settings);
    }

    // == Use Handling ==
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if(!world.isClient){
            boolean ReaperState = getReaperState(stack);

            if(!user.isSneaking()){
                if(ReaperState){
                    reaperSkill_NoEscape(world, user);
                } else {
                    skillOne_DeathMist(world, user);
                }
            } else {
                skillTwo_DeathPossession(user, stack);
            }
        }

        return TypedActionResult.success(stack, world.isClient);
    }

    // Skills
    private void skillOne_DeathMist(World world, PlayerEntity user) {
        // Apply cooldown
        user.getItemCooldownManager().set(this, 200); // 10s

        // Define area
        Box mistBox = user.getBoundingBox().expand(5.0f);

        // Get entities inside
        world.getOtherEntities(user, mistBox, e -> e instanceof LivingEntity && e != user)
                .forEach(entity -> {
                    if(entity instanceof LivingEntity living) {
                        // True damage (ignores armor)
                        dealTrueDamage(user, living);
                        // Apply Wither
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1)); // 5s Wither II
                    }
                });

        // === Particle Effects ===
        spawnMistParticles(world, user);
    }



    private void skillTwo_DeathPossession(PlayerEntity user, ItemStack stack) {
        setReaperState(stack, true);

    }

    private void reaperSkill_NoEscape(World world, PlayerEntity user) {

    }

    // == Helpers ==
    protected void dealTrueDamage(PlayerEntity user, LivingEntity target) {
        if (user.getWorld().isClient) return;
        ItemStack stack = user.getMainHandStack();
        ServerWorld sw = (ServerWorld) user.getWorld();

        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float enchBonus = EnchantmentHelper.getDamage(sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage);
        float totalDamage = (baseDamage + enchBonus);

        ItemEnchantmentsComponent enchComp = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        RegistryEntry<Enchantment> fireAspectEntry = sw.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.FIRE_ASPECT);
        int fireLevel = enchComp.getLevel(fireAspectEntry);
        if (fireLevel > 0) target.setOnFireFor(4 * fireLevel);

        DamageSource src = user.getDamageSources().magic();
        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;
    }

    private void spawnMistParticles(World world, PlayerEntity user) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    user.getX(),
                    user.getY(),
                    user.getZ(),
                    1200, 5, 5, 5, 0.2
            );
            sw.spawnParticles(
                    ParticleTypes.SOUL,
                    user.getX(), user.getY(), user.getZ(),
                    600, 5, 5, 5, 0.4
            );
        }
    }
}
