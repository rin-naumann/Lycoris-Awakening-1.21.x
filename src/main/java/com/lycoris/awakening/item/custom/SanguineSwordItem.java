package com.lycoris.awakening.item.custom;

import com.lycoris.awakening.component.ModDataComponentTypes;
import com.lycoris.awakening.weather.CustomWeatherType;
import com.lycoris.awakening.weather.WeatherNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.List;

public class SanguineSwordItem extends SwordItem{
    private static final int MAX_CHARGES = 5;
    private long useStartTick = -1;


    // Debug prefixes for chat
    private static final String SKILL_ONE_NAME = "Hemorrhage";
    private static final String SKILL_TWO_NAME = "Paralysis";
    private static final String SKILL_THREE_NAME = "Crimson Dance";
    private static final String ULTIMATE_NAME = "Veiled Moon";

    public SanguineSwordItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, settings);
    }

    /* === SERVER TICK HANDLER FOR WEAPON === */
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {

        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player.isSneaking()) {
                ItemStack stack = player.getStackInHand(hand);
                triggerSanguineMoon(player, stack, world);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }


    /* === SANGUINE CHARGES STORAGE === */
    public int getCharges(ItemStack stack) {
        return stack.getOrDefault(ModDataComponentTypes.SANGUINE_CHARGES, 0);
    }

    public void setCharges(ItemStack stack, int value) {
        stack.set(ModDataComponentTypes.SANGUINE_CHARGES, Math.max(0, Math.min(MAX_CHARGES, value)));
    }

    public void addCharges(ItemStack stack, int amount) {
        setCharges(stack, getCharges(stack) + amount);
    }

    public void consumeCharges(ItemStack stack, int amount) {
        setCharges(stack, getCharges(stack) - amount);
    }

    /* === ITEM USE LOGIC === */

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW; // temporary animation
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!user.isSneaking()) {
            // === Right-click (no shift) → Sacrifice vs Hemorrhage ===
            if (user.isSneaking()) return TypedActionResult.pass(stack); // not needed, but safe

            // Start charging (for Hemorrhage detection)
            useStartTick = world.getTime(); // mark start time
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        } else {
            // Sneaking right-click → start charging (Paralysis/Crimson)
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;

        if (useStartTick < 0) return; // not valid

        long heldTicks = world.getTime() - useStartTick;
        useStartTick = -1; // reset

        int charges = getCharges(stack);

        if (!world.isClient) {
            if (!player.isSneaking()) {
                if (heldTicks < 10) {
                    player.sendMessage(Text.literal("Bleed. " + charges + " / 5"), true);
                    triggerSacrifice(player, stack, world);   // tap (no shift)
                } else {
                    player.sendMessage(Text.literal("Activating Hemorrhage"), true);
                    triggerHemorrhage(player, stack, world);  // hold (no shift)
                }
            } else {
                if (heldTicks < 10) {
                    player.sendMessage(Text.literal("Activating Paralysis"), true);
                    triggerParalysis(player, stack, world);   // tap (shift)
                } else {
                    player.sendMessage(Text.literal("Activating Crimson Dance"), true);
                    triggerCrimsonDance(player, stack, world); // hold (shift)
                }
            }
        }
    }

    /* === PLACEHOLDER ABILITY METHODS (debug only) === */

    private void triggerSacrifice(PlayerEntity player, ItemStack stack, World world) {

    }

    private void triggerHemorrhage(PlayerEntity player, ItemStack stack, World world) {

    }

    private void triggerParalysis(PlayerEntity player, ItemStack stack, World world) {

    }

    private void triggerCrimsonDance(PlayerEntity player, ItemStack stack, World world) {

    }

    private static void triggerSanguineMoon(PlayerEntity player, ItemStack stack, World world) {
        if (!world.isClient() && world instanceof ServerWorld sw) {
            WeatherNetworking.sendWeatherStart(sw, CustomWeatherType.SANGUINE_MOON, 20 * 60 * 3);

            // Entrance animation: apply blindness to everyone
            for (ServerPlayerEntity target : sw.getPlayers()) {
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.BLINDNESS, 60, 0, false, false, false
                ));
            }
        }
    }

    /* === HELPERS === */


    /* === TOOLTIP DEBUG === */

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Blood Boons: " + getCharges(stack) + "/" + MAX_CHARGES));
    }
}
