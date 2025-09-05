package com.lycoris.awakening.item.custom;

import com.lycoris.awakening.component.ModDataComponentTypes;
import com.lycoris.awakening.effect.ModEffects;
import com.lycoris.awakening.sound.ModSounds;
import com.lycoris.awakening.util.WeaponAttackManager;
import com.lycoris.awakening.weather.CustomWeatherType;
import com.lycoris.awakening.weather.WeatherNetworking;
import com.lycoris.awakening.entity.custom.projectile.SanguineProjectileEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class SanguineSwordItem extends SwordItem{
    private static int MAX_CHARGES = 5;

    private static final Map<UUID, CopyOnWriteArrayList<ScheduledAction>> scheduledActions = new ConcurrentHashMap<>();

    public SanguineSwordItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, settings);
    }

    /* === SERVER TICK HANDLER FOR WEAPON === */
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            final int now = server.getTicks();

            // iterate the map safely; each player's list is CopyOnWriteArrayList
            scheduledActions.forEach((uuid, list) -> {
                // collect due actions first (no mutation during iteration)
                java.util.List<ScheduledAction> due = new java.util.ArrayList<>();
                for (ScheduledAction sa : list) {
                    if (sa.runAtTick <= now) due.add(sa);
                }
                if (!due.isEmpty()) {
                    // remove all due actions from the live list
                    list.removeAll(due);
                }
                // run them after we've stopped touching the list (so they can schedule new stuff)
                for (ScheduledAction sa : due) {
                    try {
                        sa.action().run();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            });
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player.isSneaking()) {
                if(player.getStackInHand(Hand.MAIN_HAND).getItem() instanceof SanguineSwordItem){
                    triggerSanguineMoon(player, player.getStackInHand(hand), world);
                    return ActionResult.FAIL;
                }

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

    public void setMaxCharges(ItemStack stack, int value) {
        if (stack.getItem() instanceof SanguineSwordItem) {
            MAX_CHARGES = value;
        }
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

        user.setCurrentHand(hand);

        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(user instanceof ServerPlayerEntity player)) return;
        if (world.isClient) return; // only run on server

        int usedTicks = getMaxUseTime(stack, user) - remainingUseTicks;

        if (player.isSneaking()) {
            if (usedTicks >= 10 && usedTicks < 20) player.sendMessage(Text.literal("Crimson Dance ▊").formatted(Formatting.RED), true);
            else if (usedTicks >= 10 && usedTicks < 30) player.sendMessage(Text.literal("Crimson Dance ▊▊").formatted(Formatting.RED), true);
            else if (usedTicks > 30 && MAX_CHARGES != 6) player.sendMessage(Text.literal("Crimson Dance ▊▊").formatted(Formatting.RED), true);
            else if (usedTicks > 30) player.sendMessage(Text.literal("Crimson Dance ▊▊▊").formatted(Formatting.DARK_RED), true);
        } else {
            // Paralysis messages (unchanged)
            if (usedTicks > 10 && usedTicks < 40) player.sendMessage(Text.literal("Charging Paralysis ▊").formatted(Formatting.RED), true);
            else if (usedTicks > 40) player.sendMessage(Text.literal("Charging Paralysis ▊▊").formatted(Formatting.DARK_RED), true);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;

        int usedTicks = getMaxUseTime(stack, user) - remainingUseTicks;

        if (!world.isClient) {
            if (player.isSneaking()) {
                if (usedTicks < 10) triggerHemorrhage(player, stack, world);
                else if (usedTicks < 20) triggerCrimsonDance(player, stack, world, 1);
                else if (usedTicks < 30) triggerCrimsonDance(player, stack, world, 2);
                else if (usedTicks > 30 && MAX_CHARGES != 6) triggerCrimsonDance(player, stack, world, 2);
                else triggerCrimsonDance(player, stack, world, 3);
            } else {
                if (usedTicks < 10) triggerSacrifice(player, stack, world);
                 else triggerParalysis(player, stack, world, usedTicks >= 40);
            }
        }
    }

    /* === ABILITY METHODS === */

    private void triggerSacrifice(PlayerEntity player, ItemStack stack, World world) {
        if (player.getHealth() > 4.0f) {
            player.timeUntilRegen = 0;
            player.damage(player.getDamageSources().outOfWorld(), 4.0f);
            addCharges(stack, 1);
            player.sendMessage(Text.literal("Charged || " + getCharges(stack) + " / " + MAX_CHARGES).formatted(Formatting.RED), true);
        } else {
            player.sendMessage(Text.literal("Too weak to charge the weapon.").formatted(Formatting.GRAY), true);
        }
    }

    private void triggerParalysis(PlayerEntity player, ItemStack stack, World world, boolean enhanced) {
        if (!(world instanceof ServerWorld sw)) return;

        player.getItemCooldownManager().set(stack.getItem(), 10);

        int cost = enhanced ? 2 : 1;
        if (getCharges(stack) < cost) {
            player.sendMessage(Text.literal("Not enough charges.").formatted(Formatting.GRAY), true);
            return;
        }

        consumeCharges(stack, cost);

        if (enhanced) {
            player.sendMessage(Text.literal("Blood Arts: Paralysis [Enhanced]").formatted(Formatting.DARK_RED), true);
            triggerEnhancedParalysis(player, stack, sw);
            return;
        }

        world.playSound(null, player.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // === Normal projectile version ===
        player.sendMessage(Text.literal("Blood Arts: Paralysis").formatted(Formatting.RED), true);

        SanguineProjectileEntity projectile = new SanguineProjectileEntity(sw, player, false);
        projectile.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());

        float speed = 2.0f;
        projectile.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, speed, 0.0f);

        sw.spawnEntity(projectile);
    }

    private void triggerEnhancedParalysis(PlayerEntity player, ItemStack stack, ServerWorld sw) {

        setMaxCharges(stack,6);

        Vec3d start = player.getPos().add(0, player.getEyeHeight(player.getPose()), 0);
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        double range = 30.0;
        Vec3d end = start.add(direction.multiply(range));

        // Find all entities along the line
        Box hitBox = new Box(start, end).expand(1.5);
        List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, hitBox,
                t -> t.isAlive() && t != player);

        if (targets.isEmpty()) {
            player.sendMessage(Text.literal("No targets hit.").formatted(Formatting.GRAY), true);
            return;
        }

        for (LivingEntity target : targets) {
            WeaponAttackManager.startMultiSlash(
                    target,
                    5,      // total slashes
                    5f,     //size
                    2,      // delay between slashes (~0.1s)
                    List.of(new WeaponAttackManager.ParticleConfig(
                            new DustParticleEffect(new Vector3f(0.9f, 0.0f, 0.0f), 1.5f),
                            1.0f, 0.05, 2
                    )),
                    living -> {
                        sw.playSound(null, target.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 0.6f, 1.0f);
                        attackWithFullDamage(player, living, 0.25f);
                        living.addStatusEffect(new StatusEffectInstance(ModEffects.ENSANGUINED, 100, 1, false, true, true));
                    }
            );
        }
    }

    private void triggerHemorrhage(PlayerEntity player, ItemStack stack, World world) {
        if (!(world instanceof ServerWorld sw)) return;

        player.getItemCooldownManager().set(stack.getItem(), 10);

        world.playSound(null, player.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);

        if (getCharges(stack) == 0) {
            player.sendMessage(Text.literal("Not enough charges.").formatted(Formatting.GRAY), true);
            return;
        }

        sw.spawnParticles(
                new DustParticleEffect(new Vector3f(0.7f, 0.0f, 0.0f), 2f),
                player.getX(), player.getY(), player.getZ(),
                150,
                1.5,1.5, 1.5,
                0.3f
        );

        if (player instanceof ServerPlayerEntity sp) {
            consumeCharges(stack, 1);
            sp.sendMessage(Text.literal("Blood Arts: Hemorrhage").formatted(Formatting.RED), true);

            Vec3d direction = sp.getRotationVec(1.0F).normalize();
            Vec3d startPos = sp.getPos();

            //Particle initialized
            List<WeaponAttackManager.ParticleConfig> cfgs = List.of(
                    new WeaponAttackManager.ParticleConfig(
                            new DustParticleEffect(new Vector3f(0.7f, 0.0f, 0.0f), 1.2f),
                            0.3f,
                            0.2f,
                            15
                    )
            );

            WeaponAttackManager.startDash(
                    sp,
                    direction,
                    startPos,
                    4,
                    1.5f,
                    cfgs,
                     user -> {
                        Box dashBox = new Box(startPos, user.getPos()).expand(1.5f);

                        List<LivingEntity> targets = world.getEntitiesByClass(
                                LivingEntity.class,
                                dashBox,
                                t -> t.isAlive() && t != user
                        );

                        for (LivingEntity t : targets) {
                            attackWithFullDamage(user, t, 1.0f);

                            StatusEffectInstance prev = t.getStatusEffect(ModEffects.ENSANGUINED);
                            int amp = (prev == null ? 0 : prev.getAmplifier() + 1);
                            t.addStatusEffect(new StatusEffectInstance(ModEffects.ENSANGUINED, 200, amp, false, true, true));

                            sw.spawnParticles(
                                    new DustParticleEffect(new Vector3f(0.7f, 0.0f, 0.0f), 2f),
                                    t.getX(), t.getY(), t.getZ(),
                                    150,
                                    1.5,1.5, 1.5,
                                    0.3f
                            );
                        }
                    }
            );
        }
    }

    private void triggerCrimsonDance(PlayerEntity player, ItemStack stack, World world, int level) {
        if (!(world instanceof ServerWorld sw)) return;
        if (getCharges(stack) == 0) {
            player.sendMessage(Text.literal("Not enough charges.").formatted(Formatting.GRAY), true);
            return;
        }

        switch (level) {
            case 1:
                consumeCharges(stack, 2);
                player.getItemCooldownManager().set(stack.getItem(), 10);
                fireProjectiles((ServerPlayerEntity) player, 3, 10);
                break;
            case 2:
                consumeCharges(stack, 4);
                player.getItemCooldownManager().set(stack.getItem(), 10);
                fireProjectiles((ServerPlayerEntity) player, 5, 15);
                break;
            case 3:
                consumeCharges(stack, 6);
                setMaxCharges(stack, 5);
                player.getItemCooldownManager().set(stack.getItem(), 20);
                ServerPlayerEntity sp = (ServerPlayerEntity) player;

                // Phase 1 immediately
                crimsonDancePhase1(sp, sw);

                // Phase 2 after 20 ticks
                schedule(sw, sp.getUuid(), 20, p -> crimsonDancePhase2(p, sw));

                // Phase 3 after 40 ticks
                schedule(sw, sp.getUuid(), 40, p -> {
                    Vec3d ghost = crimsonDancePhase3(p, sw);
                    // Phase 4 after another 20 ticks
                    schedule(sw, p.getUuid(), 20, q -> crimsonDancePhase4(q, sw, ghost));
                });
        }
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

    public static void attackWithFullDamage(PlayerEntity user, LivingEntity target, float multiplier) {
        if (user.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) user.getWorld();
        ItemStack stack = user.getMainHandStack();

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

        stack.damage(1, user, EquipmentSlot.MAINHAND);
    }

    private static void fireProjectiles(ServerPlayerEntity player, int count, double spreadDeg) {
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        float speed = 2.0f;

        for (int i = 0; i < count; i++) {
            double angleOffset = Math.toRadians((i - (count - 1) / 2.0) * spreadDeg);
            double cos = Math.cos(angleOffset), sin = Math.sin(angleOffset);
            double rx = look.x * cos - look.z * sin;
            double rz = look.x * sin + look.z * cos;
            Vec3d dir = new Vec3d(rx, look.y, rz).normalize();

            world.playSound(null, player.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 0.6f, 1.0f);
            SanguineProjectileEntity proj = new SanguineProjectileEntity(world, player, true);
            proj.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
            proj.setVelocity(dir.x, dir.y, dir.z, speed, 0.0f);
            world.spawnEntity(proj);
        }
    }

    private void crimsonDancePhase1(ServerPlayerEntity player, ServerWorld world) {
        Vec3d center = player.getPos();
        double radius = 15.0;

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                new Box(center.add(-radius, -radius, -radius), center.add(radius, radius, radius)),
                t -> t.isAlive() && t != player
        );

        for (LivingEntity target : targets) {
            Vec3d pull = center.subtract(target.getPos()).normalize().multiply(1.0); // pull strength
            target.addVelocity(pull.x, pull.y * 0.1, pull.z);
            target.velocityModified = true;

            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 4, false, true, true));
        }

        // particles pulling inward
        for (int i = 0; i < 100; i++) {
            Vec3d randPos = center.add(
                    (world.random.nextDouble() - 0.5) * radius * 2,
                    (world.random.nextDouble() - 0.5) * radius * 2,
                    (world.random.nextDouble() - 0.5) * radius * 2
            );
            Vec3d vel = center.subtract(randPos).normalize().multiply(0.2);
            world.spawnParticles(
                    new DustParticleEffect(new Vector3f(0.9f, 0.0f, 0.0f), 2.0f),
                    randPos.x, randPos.y, randPos.z,
                    1, vel.x, vel.y, vel.z, 0.1
            );
        }
    }

    private void crimsonDancePhase2(ServerPlayerEntity player, ServerWorld world) {
        double radius = 15.0;

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(radius),
                t -> t.isAlive() && t != player
        );

        if (targets.isEmpty()) return;

        for (LivingEntity target : targets) {
            // Schedule 8 slashes on this target
            WeaponAttackManager.startMultiSlash(
                    target,
                    8,
                    15f,
                    1,
                    List.of(new WeaponAttackManager.ParticleConfig(
                            new DustParticleEffect(new Vector3f(0.9f, 0.0f, 0.0f), 1.5f),
                            1.0f, 0.05, 2
                    )),
                    living -> {
                        world.playSound(null, target.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 0.6f, 1.0f);
                        attackWithFullDamage(player, living, 0.25f);
                        living.addStatusEffect(new StatusEffectInstance(ModEffects.ENSANGUINED, 100, 1, false, true, true));
                    }
            );

        }
    }

    // --- Phase 3: Dash backward and leave ghost ---
    private Vec3d crimsonDancePhase3(ServerPlayerEntity player, ServerWorld world) {
        // Save the ghost position before the dash
        Vec3d ghostPos = player.getPos();

        // Dash backwards by applying velocity opposite to look direction
        Vec3d back = player.getRotationVec(1.0f).normalize().multiply(-1.5);

        WeaponAttackManager.startDash(
                player,
                back,
                ghostPos,
                4,
                1.5f,
                List.of(
                        new WeaponAttackManager.ParticleConfig(
                                new DustParticleEffect(new Vector3f(0.7f, 0.0f, 0.0f), 1.2f),
                                0.3f,
                                0.2f,
                                15
                        )
                ),
                p -> {}
        );

        return ghostPos;
    }

    // --- Phase 4: Explosion at ghost position ---
    private void crimsonDancePhase4(ServerPlayerEntity player, ServerWorld world, Vec3d ghostPos) {
        if (ghostPos == null) return;

        double radius = 15.0;

        // Particles for each spin
        List<WeaponAttackManager.ParticleConfig> particles = List.of(
                new WeaponAttackManager.ParticleConfig(
                        new DustParticleEffect(new Vector3f(0.9f, 0.0f, 0.0f), 1.5f),
                        1.0f, 0.05, 4
                )
        );

        DamageSource dmgsrc = player.getDamageSources().playerAttack(player);
        float[] offset = {-3, 1, 0.5f};

        // Spin sequence (angles in degrees)
        int[] angles = {20, -30, 80, -10, 60, -70, 0, 45, -45};
        int delay = 0;
        float yawDeg = player.getYaw();
        for (int angle : angles) {
            final int tiltDeg = angle;
            final int startDelay = delay;

            schedule(world, player.getUuid(), startDelay, p -> {
                        WeaponAttackManager.startAngledSpinFromPos(
                                player,
                                4,
                                0,
                                radius,
                                0,
                                yawDeg,
                                tiltDeg,
                                dmgsrc,
                                offset,
                                particles,
                                ghostPos
                        );
                        world.playSound(null, player.getBlockPos(), ModSounds.SLASH, SoundCategory.PLAYERS, 1.0f, 1.0f);
            });

            delay += 4;
        }
    }

    private static void schedule(ServerWorld world, UUID playerId, int delayTicks, Consumer<ServerPlayerEntity> action) {
        final int runAt = world.getServer().getTicks() + Math.max(0, delayTicks);

        scheduledActions
                .computeIfAbsent(playerId, k -> new CopyOnWriteArrayList<>())
                .add(new ScheduledAction(runAt, () -> {
                    ServerPlayerEntity sp = world.getServer().getPlayerManager().getPlayer(playerId);
                    if (sp != null && sp.isAlive()) {
                        action.accept(sp);
                    }
                }));
    }

    /* === TOOLTIP DEBUG === */

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Blood Boons: " + getCharges(stack) + "/" + MAX_CHARGES));
    }

    /* === DATA CLASSES === */
    private record ScheduledAction(int runAtTick, Runnable action) {}
}
