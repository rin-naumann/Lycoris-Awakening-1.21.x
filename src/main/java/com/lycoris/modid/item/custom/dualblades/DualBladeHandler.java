package com.lycoris.modid.item.custom.dualblades;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DualBladeHandler {
    // Tracks active combos per player
    private static final Map<UUID, ComboData> activeCombos = new HashMap<>();
    private static final Map<UUID, Long> lastInputTime = new HashMap<>();
    private static final Map<UUID, SlashAnim> ACTIVE_SLASHES = new HashMap<>();
    private static final long INPUT_COOLDOWN_MS = 200; // 0.2s debounce

    public static void init() {
        // Server Tick Events
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();
                ComboData data = activeCombos.get(id);
                if (data != null && data.isActive()) {
                    data.tick(player);
                }

                SlashAnim anim = ACTIVE_SLASHES.get(id);
                if (anim != null) {
                    if (!animateSlash(player, anim)) {
                        ACTIVE_SLASHES.remove(id);
                    }
                }
            }
        });

        // Left-clicks
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && hasDualBlades(player)) {
                handleLeftClick(player, hand);
                return ActionResult.FAIL; // prevent vanilla attack
            }
            return ActionResult.PASS; // normal attack for other weapons
        });

        AttackBlockCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient) {
                handleLeftClick(player, hand);
            }
            return ActionResult.PASS;
        });

        // Right-clicks
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient) {
                handleRightClick(player, hand);
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    public static void handleLeftClick(PlayerEntity player, Hand hand) {
        if (!hasDualBlades(player)) return;
        handleComboInput(player, "LEFT", hand);
    }

    public static void handleRightClick(PlayerEntity player, Hand hand) {
        if (!hasDualBlades(player)) return;
        handleComboInput(player, "RIGHT", hand);
        player.swingHand(Hand.OFF_HAND, true);
    }

    private static boolean hasDualBlades(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();
        return (main.getItem() instanceof LucidityItem && off.getItem() instanceof DelusionItem)
                || (main.getItem() instanceof DelusionItem && off.getItem() instanceof LucidityItem);
    }

    private static void handleComboInput(PlayerEntity player, String input, Hand hand) {
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();

        long last = lastInputTime.getOrDefault(id, 0L);
        if (now - last < INPUT_COOLDOWN_MS) return;
        lastInputTime.put(id, now);

        ComboData data = activeCombos.getOrDefault(id, new ComboData());

        if (data.isWaitingFor(input)) {
            data.advanceCombo(player, hand);
            triggerArcAttack(player, hand, input); // 🔥 spawn slash attack+anim here
        } else {
            player.sendMessage(Text.literal("Combo Lost").formatted(Formatting.RED), true);
            data.reset();
        }
        activeCombos.put(id, data);
    }

    // === Helper Methods ===
    public static void attackWithMultiplier(PlayerEntity user, LivingEntity target, Hand hand, float multiplier) {
        if (user.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) user.getWorld();
        ItemStack stack = user.getStackInHand(hand);

        // Base + enchantments
        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float enchBonus = EnchantmentHelper.getDamage(sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage);
        float totalDamage = (baseDamage + enchBonus) * multiplier;

        // Critical check
        boolean crit = user.fallDistance > 0.0F && !user.isOnGround() && !user.isClimbing()
                && !user.isTouchingWater() && !user.hasStatusEffect(StatusEffects.BLINDNESS)
                && !user.hasVehicle() && !user.isSprinting();
        if (crit) totalDamage *= 1.5F;

        // Fire aspect
        ItemEnchantmentsComponent enchComp = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        int fireLevel = enchComp.getLevel(sw.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.FIRE_ASPECT));
        if (fireLevel > 0) target.setOnFireFor(4 * fireLevel);

        // Deal damage
        DamageSource src = user.getDamageSources().playerAttack(user);
        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;

        // Durability
        EquipmentSlot slot = (hand == Hand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.damage(1, user, slot);
    }

    private static void triggerArcAttack(PlayerEntity user, Hand hand, String input) {
        if (!(user.getWorld() instanceof ServerWorld sw)) return;

        double attackRadius = user.getAttributeValue(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        double coneDeg = 90.0;
        double coneCos = Math.cos(Math.toRadians(coneDeg));
        Vec3d look = user.getRotationVec(1.0F).normalize();

        // --- Deal damage once ---
        List<LivingEntity> nearby = sw.getEntitiesByClass(
                LivingEntity.class,
                user.getBoundingBox().expand(attackRadius),
                e -> e.isAlive() && e != user
        );

        for (LivingEntity target : nearby) {
            Vec3d toTarget = target.getPos().subtract(user.getPos());
            double dist = toTarget.length();

            if (dist <= attackRadius) {
                Vec3d dir = toTarget.normalize();
                double dot = dir.dotProduct(look);
                if (dot >= coneCos) {
                    attackWithMultiplier(user, target, hand, 1.0f);
                }
            }
        }

        // --- Lock yaw and tilt ---
        double baseYaw = Math.toRadians(user.getYaw()); // radians at attack start
        double randomTilt = Math.toRadians(-45 + sw.random.nextInt(91));

        // Slash arc centered on look direction
        double sweepRange = Math.PI; // 180° fixed
        double offset = Math.toRadians(90); // rotate entire slash 90° to the right
        double start = -sweepRange / 2 + offset;
        double end   = +sweepRange / 2 + offset;

        // Flip sweep direction
        if ("LEFT".equals(input)) {
            double tmp = start;
            start = end;
            end = tmp;
        }

        ACTIVE_SLASHES.put(user.getUuid(), new SlashAnim(start, end, 3.0, 5, randomTilt, baseYaw));
    }

    private static boolean animateSlash(PlayerEntity player, SlashAnim anim) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;
        if (!player.isAlive()) return false;

        int tickIndex = anim.totalTicks - anim.ticksLeft;
        double progress = (double) tickIndex / anim.totalTicks;
        double localAngle = anim.start + (anim.end - anim.start) * progress;

        double arcHalfWidth = Math.toRadians(12);
        int samples = 24;

        // Position at chest height, slightly forward
        double yBase = player.getBodyY(0.9);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double forwardOffset = 0.6;
        double cx = player.getX() + look.x * forwardOffset;
        double cz = player.getZ() + look.z * forwardOffset;

        // Use locked yaw from trigger time
        double yawRad = anim.baseYaw;
        double tilt = anim.tilt;

        for (int s = 0; s < samples; s++) {
            double t = (s / (double)(samples - 1)) * 2.0 - 1.0;
            double a = localAngle + t * arcHalfWidth;

            for (double offset = 0.2; offset <= anim.radius; offset += 0.25) {
                double lx = Math.cos(a) * offset;
                double lz = Math.sin(a) * offset;

                // --- Apply tilt (around local Z axis, so left vs right edges are offset in Y) ---
                double ly = lx * Math.sin(tilt);
                double lxTilted = lx * Math.cos(tilt);

                // Rotate arc into world using yaw
                double wx = lxTilted * Math.cos(yawRad) - lz * Math.sin(yawRad);
                double wz = lxTilted * Math.sin(yawRad) + lz * Math.cos(yawRad);
                double wy = yBase + ly;

                double px = cx + wx;
                double pz = cz + wz;

                if (sw.random.nextFloat() < 0.4F) {
                    sw.spawnParticles(ParticleTypes.CRIT, px, wy, pz, 1, 0.01, 0.01, 0.01, 0.0);
                }
            }
        }

        anim.ticksLeft--;
        return anim.ticksLeft > 0;
    }

    // === SlashAnim ===
    public static class SlashAnim {
        public final double start;
        public final double end;
        public final double radius;
        public final int totalTicks;
        public int ticksLeft;
        public final double tilt;
        public final double baseYaw; // locked at trigger

        public SlashAnim(double start, double end, double radius, int totalTicks, double tilt, double baseYaw) {
            this.start = start;
            this.end = end;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.tilt = tilt;
            this.baseYaw = baseYaw;
        }
    }


    private static class ComboData {
        private String expected = "LEFT";
        private int successCount = 0;
        private long expireTime = 0; // ms timestamp
        private long lastAnnounce = 0; // last second announced
        private Hand lastUsedHand = Hand.MAIN_HAND;

        public boolean isWaitingFor(String input) {
            return input.equals(expected);
        }

        public void advanceCombo(PlayerEntity player, Hand hand) {
            successCount++;
            lastUsedHand = hand;

            // Roll a coin flip: 50% LEFT, 50% RIGHT
            expected = Math.random() < 0.5 ? "LEFT" : "RIGHT";

            player.sendMessage(Text.literal(successCount + " || Next : " + expected), true);

            if (successCount >= 3) {
                enterFlowState(player);
            }
        }

        public void reset() {
            expected = "LEFT";
            successCount = 0;
            expireTime = 0;
            lastAnnounce = 0;
            lastUsedHand = Hand.MAIN_HAND;
        }

        public boolean isExpired() {
            return expireTime > 0 && System.currentTimeMillis() > expireTime;
        }

        public boolean isActive() {
            return expireTime > 0;
        }

        public void tick(PlayerEntity player) {
            if (!isActive()) return;

            long timeLeft = (expireTime - System.currentTimeMillis()) / 1000;

            // Expired?
            if (timeLeft < 0) {
                player.sendMessage(Text.literal("Combo expired!"), true);
                reset();
                return;
            }

            // Announce only when the second changes
            if (timeLeft < lastAnnounce) {
                lastAnnounce = timeLeft;
                player.sendMessage(Text.literal("Next Input: " + expected + " (" + timeLeft + "s)"), true);
            }
        }

        private void enterFlowState(PlayerEntity player) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, true, false));
        }
    }
}
