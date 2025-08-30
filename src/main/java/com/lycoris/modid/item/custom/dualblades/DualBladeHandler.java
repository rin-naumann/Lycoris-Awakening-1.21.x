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
    private static final Map<UUID, ComboData> activeCombos = new HashMap<>();
    private static final Map<UUID, Long> lastInputTime = new HashMap<>();
    private static final Map<UUID, SlashAnim> ACTIVE_SLASHES = new HashMap<>();
    private static final long INPUT_COOLDOWN_MS = 200;
    private static final Map<UUID, Long> comboLockout = new HashMap<>();
    private static final Map<UUID, Long> cooldownAnnounce = new HashMap<>();

    public static void init() {

        // Tick Handler
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();

            for (PlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();

                ComboData data = activeCombos.get(id);
                if (data != null) {
                    data.tick(player, now);
                }

                Long lockoutEnd = comboLockout.get(id);
                if (lockoutEnd != null) {
                    if (now >= lockoutEnd) {
                        comboLockout.remove(id);
                        cooldownAnnounce.remove(id);
                        player.sendMessage(Text.literal("Combo ready!").formatted(Formatting.GREEN), true);
                    } else {
                        long secondsLeft = (lockoutEnd - now) / 1000;
                        if (secondsLeft != cooldownAnnounce.getOrDefault(id, -1L)) {
                            cooldownAnnounce.put(id, secondsLeft);
                            player.sendMessage(Text.literal("Combo on cooldown (" + secondsLeft + "s)").formatted(Formatting.RED), true);
                        }
                    }
                }

                SlashAnim anim = ACTIVE_SLASHES.get(id);
                if (anim != null) {
                    if (!animateSlash(player, anim)) {
                        ACTIVE_SLASHES.remove(id);
                    }
                }
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && hasDualBlades(player) && !player.isSneaking()) {
                handleLeftClick(player, hand);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && hasDualBlades(player) && !player.isSneaking()) {
                handleLeftClick(player, hand);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && hasDualBlades(player) && !player.isSneaking()) {
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

        Long lockoutEnd = comboLockout.get(id);
        if (lockoutEnd != null && now < lockoutEnd) {
            player.sendMessage(Text.literal("Combo unavailable (" + ((lockoutEnd - now) / 1000) + "s)").formatted(Formatting.RED), true);
            return;
        }

        long last = lastInputTime.getOrDefault(id, 0L);
        if (now - last < INPUT_COOLDOWN_MS) return;
        lastInputTime.put(id, now);

        ComboData data = activeCombos.getOrDefault(id, new ComboData());

        if (data.isWaitingFor(input)) {
            data.advanceCombo(player, hand);
            triggerArcAttack(player, hand, input);

            // 🔥 Nudge forward if in flow state
            if (data.isInFlow()) {
                Vec3d look = player.getRotationVec(1.0F).normalize();
                double nudgeStrength = 0.25;
                player.addVelocity(look.x * nudgeStrength, 0, look.z * nudgeStrength);
                player.velocityModified = true;
            }
        } else {
            data.reset(player);
        }
        activeCombos.put(id, data);
    }

    public static void extendComboWindow(PlayerEntity player, Hand hand, long windowMs) {
        UUID id = player.getUuid();
        ComboData data = activeCombos.getOrDefault(id, new ComboData());
        clearLockout(player); // ensure lockout doesn’t block

        data.advanceComboWithWindow(player, hand, windowMs);
        activeCombos.put(id, data);
    }

    public static void clearLockout(PlayerEntity player) {
        UUID id = player.getUuid();
        comboLockout.remove(id);
        cooldownAnnounce.remove(id);
    }

    // === Damage + Arc ===
    public static void attackWithMultiplier(PlayerEntity user, LivingEntity target, Hand hand, float multiplier) {
        if (user.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) user.getWorld();
        ItemStack stack = user.getStackInHand(hand);

        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float enchBonus = EnchantmentHelper.getDamage(sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage);
        float totalDamage = (baseDamage + enchBonus) * multiplier;

        boolean crit = user.fallDistance > 0.0F && !user.isOnGround() && !user.isClimbing()
                && !user.isTouchingWater() && !user.hasStatusEffect(StatusEffects.BLINDNESS)
                && !user.hasVehicle() && !user.isSprinting();
        if (crit) totalDamage *= 1.5F;

        ItemEnchantmentsComponent enchComp = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        int fireLevel = enchComp.getLevel(sw.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.FIRE_ASPECT));
        if (fireLevel > 0) target.setOnFireFor(4 * fireLevel);

        DamageSource src = user.getDamageSources().playerAttack(user);
        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;

        EquipmentSlot slot = (hand == Hand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.damage(1, user, slot);
    }

    private static void triggerArcAttack(PlayerEntity user, Hand hand, String input) {
        if (!(user.getWorld() instanceof ServerWorld sw)) return;

        double attackRadius = user.getAttributeValue(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
        double coneDeg = 90.0;
        double coneCos = Math.cos(Math.toRadians(coneDeg));
        Vec3d look = user.getRotationVec(1.0F).normalize();

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

        double baseYaw = Math.toRadians(user.getYaw());
        double randomTilt = Math.toRadians(-45 + sw.random.nextInt(91));

        double sweepRange = Math.PI;
        double offset = Math.toRadians(90);
        double start = -sweepRange / 2 + offset;
        double end = +sweepRange / 2 + offset;

        if ("LEFT".equals(input)) {
            double tmp = start;
            start = end;
            end = tmp;
        }

        ACTIVE_SLASHES.put(user.getUuid(), new SlashAnim(start, end, 3.0, 5, randomTilt, baseYaw));

        // --- Short forward dash ---
        Vec3d dash = look.multiply(0.25); // 0.25 = ~0.25 block push
        user.addVelocity(dash.x, 0, dash.z);
        user.velocityModified = true; // force client sync
    }

    private static boolean animateSlash(PlayerEntity player, SlashAnim anim) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;
        if (!player.isAlive()) return false;

        int tickIndex = anim.totalTicks - anim.ticksLeft;
        double progress = (double) tickIndex / anim.totalTicks;
        double localAngle = anim.start + (anim.end - anim.start) * progress;

        double arcHalfWidth = Math.toRadians(12);
        int samples = 24;

        double yBase = player.getBodyY(0.9);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double forwardOffset = 0.6;
        double cx = player.getX() + look.x * forwardOffset;
        double cz = player.getZ() + look.z * forwardOffset;

        double yawRad = anim.baseYaw;
        double tilt = anim.tilt;

        for (int s = 0; s < samples; s++) {
            double t = (s / (double) (samples - 1)) * 2.0 - 1.0;
            double a = localAngle + t * arcHalfWidth;

            for (double offset = 0.2; offset <= anim.radius; offset += 0.25) {
                double lx = Math.cos(a) * offset;
                double lz = Math.sin(a) * offset;

                double ly = lx * Math.sin(tilt);
                double lxTilted = lx * Math.cos(tilt);

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

    public static class SlashAnim {
        public final double start;
        public final double end;
        public final double radius;
        public final int totalTicks;
        public int ticksLeft;
        public final double tilt;
        public final double baseYaw;

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

    public static class ComboData {
        private String expected = "LEFT";
        private int successCount = 0;
        private long expireTime = 0;
        private long lastAnnounce = -1;
        private Hand lastUsedHand = Hand.MAIN_HAND;
        private String lastMessage = "";

        public boolean isWaitingFor(String input) {
            return input.equals(expected);
        }

        public void advanceCombo(PlayerEntity player, Hand hand) {
            successCount++;
            lastUsedHand = hand;
            long windowMs;

            if (successCount >= 3) {
                enterFlowState(player);
            }

            if (successCount < 10) {
                windowMs = 2000;
            } else {
                double reduced = 1.0 - 0.1 * (successCount - 10);
                if (reduced < 0.5) reduced = 0.5;
                windowMs = (long) (reduced * 1000);
            }

            expireTime = System.currentTimeMillis() + windowMs;

            expected = Math.random() < 0.5 ? "LEFT" : "RIGHT";

            player.sendMessage(Text.literal("Combo : " + successCount +
                            " || Next : " + expected + " (" + formatTime(windowMs) + ")")
                    .formatted(Formatting.GOLD), true);

            lastAnnounce = -1;
        }

        public void advanceComboWithWindow(PlayerEntity player, Hand hand, long customWindow) {
            successCount++;
            lastUsedHand = hand;

            if (successCount >= 3) {
                enterFlowState(player);
            }

            expireTime = System.currentTimeMillis() + customWindow;
            expected = Math.random() < 0.5 ? "LEFT" : "RIGHT";

            player.sendMessage(Text.literal("Combo : " + successCount +
                            " || Next : " + expected + " (" + formatTime(customWindow) + ")")
                    .formatted(Formatting.GOLD), true);

            lastAnnounce = -1;
        }


        public void reset(PlayerEntity player) {
            expected = "LEFT";
            successCount = 0;
            expireTime = 0;
            lastAnnounce = -1;
            lastUsedHand = Hand.MAIN_HAND;

            comboLockout.put(player.getUuid(), System.currentTimeMillis() + 5000);
            player.sendMessage(Text.literal("Combo broken! 5s cooldown...").formatted(Formatting.RED), true);
        }

        public void tick(PlayerEntity player, long now) {
            if (expireTime == 0) return;

            long timeLeftMs = expireTime - now;
            if (timeLeftMs <= 0) {
                reset(player);
                return;
            }

            String display = formatTime(timeLeftMs);

            String msg = "Combo : " + successCount + " || Next : " + expected + " (" + display + ")";
            if (!msg.equals(lastMessage)) {
                lastMessage = msg;
                player.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), true);
            }
        }

        private String formatTime(long ms) {
            if (ms < 1000) {
                return String.format("%.1fs", ms / 1000.0);
            } else {
                return (ms / 1000) + "s";
            }
        }

        private void enterFlowState(PlayerEntity player) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, true, false));
        }

        public boolean isInFlow() {
            return successCount >= 3 && expireTime > System.currentTimeMillis();
        }

    }
}
