package com.lycoris.modid.item.custom;

import com.lycoris.modid.component.ModDataComponentTypes;
import com.lycoris.modid.effect.ModEffects;
import com.lycoris.modid.util.CooldownBarManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MementoMoriItem extends SwordItem {

    // == Reaper State Helpers ==
    public static boolean getReaperState(ItemStack stack){
        return Boolean.TRUE.equals(stack.get(ModDataComponentTypes.REAPER_STATE));
    }
    public static void setReaperState(ItemStack stack, boolean state) {
        stack.set(ModDataComponentTypes.REAPER_STATE, state);
    }

    // --- Skill names ---
    private static final String SKILL_NAME = "Death Mist";
    private static final String ULT_NAME   = "Death Possession";
    private static final String DUR_NAME   = "Reaper State";

    // --- Enhanced skill runtime ---
    private static final Map<UUID, PullData> ACTIVE_PULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, SlashAnim> ACTIVE_SLASHES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_POSSESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, PullVisual> ACTIVE_PULL_VISUALS = new ConcurrentHashMap<>();

    // --- Server tick handler ---
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {

            // --- Handle Cooldown System ---
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ItemStack held = player.getMainHandStack();
                if (held.getItem() instanceof MementoMoriItem) {
                    int skillCd = held.getOrDefault(ModDataComponentTypes.SKILL_COOLDOWN, 0);
                    if (skillCd > 0) {
                        held.set(ModDataComponentTypes.SKILL_COOLDOWN, skillCd - 1);
                    }
                    int ultCd = held.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);
                    if (ultCd > 0) {
                        held.set(ModDataComponentTypes.ULTIMATE_COOLDOWN, ultCd - 1);
                    }
                    int duration = held.getOrDefault(ModDataComponentTypes.ABILITY_DURATION, 0);
                    if (duration > 0){
                        held.set(ModDataComponentTypes.ABILITY_DURATION, duration - 1);
                    }

                    CooldownBarManager.updateSkillBar(player, SKILL_NAME, skillCd, 20 * 10);
                    CooldownBarManager.updateUltimateBar(player, ULT_NAME, ultCd, 20 * 60);
                    CooldownBarManager.updateDurationBar(player, DUR_NAME, duration, 20 * 30);

                } else {
                    CooldownBarManager.removeBar(player, SKILL_NAME);
                    CooldownBarManager.removeBar(player, ULT_NAME);
                    CooldownBarManager.removeBar(player, DUR_NAME);
                }
            }

            // --- Reaper trails (always on when Reaper State is active) ---
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!player.isAlive()) continue;
                if (!getReaperState(player.getMainHandStack())) continue;

                ServerWorld sw = player.getServerWorld();
                sw.spawnParticles(ParticleTypes.SOUL,
                        player.getX(), player.getBodyY(0.5), player.getZ(),
                        2, 0.25, 0.25, 0.25, 0.01);
                sw.spawnParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getBodyY(0.5), player.getZ(),
                        2, 0.25, 0.25, 0.25, 0.01);
            }

            // --- Sustained pulls ---
            for (Iterator<Map.Entry<UUID, PullData>> it = ACTIVE_PULLS.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, PullData> e = it.next();
                PullData pd = e.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(pd.playerId);
                if (player == null || !player.isAlive() || pd.ticksLeft <= 0) { it.remove(); continue; }

                ServerWorld sw = player.getServerWorld();
                Entity got = sw.getEntity(pd.targetId);
                if (!(got instanceof LivingEntity target) || !target.isAlive()) { it.remove(); continue; }

                // Gentle sustained pull
                Vec3d toward = player.getPos().subtract(target.getPos()).normalize();
                target.addVelocity(toward.x * pd.perTickStrength, 0.02, toward.z * pd.perTickStrength);
                target.velocityModified = true;

                pd.ticksLeft--;
                if (target.squaredDistanceTo(player) < 1.5) it.remove();
            }

            // --- Slash animations ---
            for (Iterator<Map.Entry<UUID, SlashAnim>> it = ACTIVE_SLASHES.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, SlashAnim> e = it.next();
                SlashAnim anim = e.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player == null || !player.isAlive()) { it.remove(); continue; }

                ServerWorld sw = player.getServerWorld();

                // Wait for start delay
                if (anim.startDelay > 0) {
                    anim.startDelay--;
                    continue;
                }

                // AoE trigger once
                if (!anim.aoeDone) {
                    double aoeRadius = anim.damageRadius;
                    List<LivingEntity> aoeTargets = sw.getEntitiesByClass(
                            LivingEntity.class,
                            player.getBoundingBox().expand(aoeRadius),
                            t -> t.isAlive() && t != player
                    );
                    for (LivingEntity t : aoeTargets) {
                        anim.source.dealTrueDamage(player, t);

                        StatusEffectInstance prev = t.getStatusEffect(ModEffects.DEATH);
                        int amp = (prev == null ? 0 : prev.getAmplifier() + 1);
                        t.addStatusEffect(new StatusEffectInstance(ModEffects.DEATH, 200, amp, false, true));
                    }
                    anim.aoeDone = true;
                }

                // Cinematic spin-slash
                int tickIndex = anim.totalTicks - anim.ticksLeft;
                double progress = (double) tickIndex / anim.totalTicks;
                double angle = anim.baseAngle + progress * (Math.PI * 2.0 * 1);

                double arcHalfWidth = Math.toRadians(60);
                int samples = 60;
                double y = player.getBodyY(0.5);

                for (int s = 0; s < samples; s++) {
                    double t = (s / (double)(samples - 1)) * 2.0 - 1.0;
                    double a = angle + t * arcHalfWidth;

                    for (double offset = -1; offset <= 3; offset += 0.3) {
                        double px = player.getX() + (anim.radius - 1.0 + offset) * Math.cos(a);
                        double pz = player.getZ() + (anim.radius - 1.0 + offset) * Math.sin(a);

                        if(sw.random.nextFloat() < 0.15F){
                            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y, pz, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                        if(sw.random.nextFloat() < 0.15F){
                            sw.spawnParticles(ParticleTypes.SMOKE, px, y, pz, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                    }
                }

                anim.ticksLeft--;
                if (anim.ticksLeft <= 0) it.remove();
            }

            // --- Pull cone visual ---
            for (Iterator<Map.Entry<UUID, PullVisual>> it = ACTIVE_PULL_VISUALS.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, PullVisual> e = it.next();
                PullVisual pv = e.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(pv.playerId);
                if (player == null || !player.isAlive()) { it.remove(); continue; }

                ServerWorld sw = player.getServerWorld();

                List<Vec3d> newParticles = new ArrayList<>();
                for (Vec3d pos : pv.particles) {
                    Vec3d toward = player.getPos().subtract(pos).normalize().multiply(0.4);
                    Vec3d newPos = pos.add(toward);

                    sw.spawnParticles(ParticleTypes.SMOKE, newPos.x, newPos.y, newPos.z, 1, 0.05, 0.05, 0.05, 0.0);
                    if (sw.random.nextFloat() < 0.2f) {
                        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, newPos.x, newPos.y, newPos.z, 1, 0, 0, 0, 0);
                    }

                    newParticles.add(newPos);
                }

                pv.particles.clear();
                pv.particles.addAll(newParticles);

                pv.ticksLeft--;
                if (pv.ticksLeft <= 0) it.remove();
            }

            // --- Death Possession timers ---
            for (Iterator<Map.Entry<UUID, Integer>> it = ACTIVE_POSSESSIONS.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, Integer> e = it.next();
                UUID id = e.getKey();
                int ticks = e.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null) { it.remove(); continue; }

                if (ticks <= 0) {
                    setReaperState(player.getMainHandStack(), false);
                    player.getMainHandStack().set(ModDataComponentTypes.ULTIMATE_COOLDOWN, 20 * 60); // 1m
                    it.remove();
                } else {
                    e.setValue(ticks - 1);
                }
            }
        });
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
            int skillCld = stack.getOrDefault(ModDataComponentTypes.SKILL_COOLDOWN, 0);
            int ultCld =stack.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);

            if(!user.isSneaking()){ // basic right click skill
                if(ReaperState){
                    if(!(skillCld > 0)){
                        reaperSkill_NoEscape(world, user);
                        stack.set(ModDataComponentTypes.SKILL_COOLDOWN, 20 * 3); // 3s
                    } else {
                        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Skill on Cooldown").formatted(Formatting.GRAY), true);
                    }
                } else {
                    if(!(skillCld > 0)){
                        skillOne_DeathMist(world, user);
                        stack.set(ModDataComponentTypes.SKILL_COOLDOWN, 20 * 10); // 10s
                    } else {
                        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Skill on Cooldown").formatted(Formatting.GRAY), true);
                    }
                }
            } else { // shift + right click ultimate
                if(!(ultCld > 0)){
                    skillTwo_DeathPossession(user, stack);
                    stack.set(ModDataComponentTypes.ABILITY_DURATION, 20 * 30); // 30s
                }
            }
        }

        return TypedActionResult.success(stack, world.isClient);
    }

    // == Skills ==
    private void skillOne_DeathMist(World world, PlayerEntity user) {

        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 3, 1, false, false, false));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20 * 3, 0, false, false, false));

        Box mistBox = user.getBoundingBox().expand(5.0f);

        world.getOtherEntities(user, mistBox, e -> e instanceof LivingEntity && e != user)
                .forEach(entity -> {
                    if(entity instanceof LivingEntity living) {
                        dealTrueDamage(user, living);
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
                    }
                });

        spawnMistParticles(world, user);
    }

    private void skillTwo_DeathPossession(PlayerEntity user, ItemStack stack) {
        setReaperState(stack, true);
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20 * 30, 1, false, false, false));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 30, 1, false, false, false));

        ACTIVE_POSSESSIONS.put(user.getUuid(), 20 * 30);
    }

    private void reaperSkill_NoEscape(World world, PlayerEntity user) {
        if (!(world instanceof ServerWorld sw)) return;

        double pullRadius = 12.0;
        double coneDeg = 60.0;
        double coneCos = Math.cos(Math.toRadians(coneDeg));
        Vec3d look = user.getRotationVec(1.0F).normalize();

        List<LivingEntity> nearby = sw.getEntitiesByClass(
                LivingEntity.class,
                user.getBoundingBox().expand(pullRadius),
                e -> e.isAlive() && e != user
        );

        int sustainTicks = 12;
        double perTickStrength = 0.2;

        for (LivingEntity target : nearby) {
            Vec3d toTarget = target.getPos().subtract(user.getPos());
            double dist = toTarget.length();
            if (dist <= pullRadius) {
                Vec3d dir = toTarget.normalize();
                double dot = dir.dotProduct(look);
                if (dot >= coneCos) {
                    Vec3d toward = user.getPos().subtract(target.getPos()).normalize();
                    target.addVelocity(toward.x * 0.6, 0.05, toward.z * 0.6);
                    target.velocityModified = true;

                    ACTIVE_PULLS.put(target.getUuid(),
                            new PullData(target.getUuid(), user.getUuid(), sustainTicks, perTickStrength));
                }
            }
        }

        double baseAngle = Math.atan2(look.z, look.x);
        ACTIVE_SLASHES.put(user.getUuid(),
                new SlashAnim(user.getUuid(), this,
                        4, 20, 3.0, 4.0, baseAngle));

        // Start pull cone particle visual
        List<Vec3d> coneParticles = new ArrayList<>();
        net.minecraft.util.math.random.Random rand = sw.getRandom();
        double coneAngle = Math.toRadians(60);
        int particleCount = 40;

        for (int i = 0; i < particleCount; i++) {
            double dist = 2.0 + rand.nextDouble() * 10.0;
            double angleOffset = (rand.nextDouble() - 0.5) * coneAngle;
            double base = Math.atan2(look.z, look.x) + angleOffset;

            double px = user.getX() + dist * Math.cos(base);
            double py = user.getBodyY(0.5) + (rand.nextDouble() - 0.5) * 1.5;
            double pz = user.getZ() + dist * Math.sin(base);

            coneParticles.add(new Vec3d(px, py, pz));
        }

        ACTIVE_PULL_VISUALS.put(user.getUuid(), new PullVisual(user.getUuid(), 15, coneParticles));
    }

    // == Helpers ==
    protected void dealTrueDamage(PlayerEntity user, LivingEntity target) {
        if (user.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) user.getWorld();
        ItemStack stack = user.getMainHandStack();

        // Base + enchantments
        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float enchBonus = EnchantmentHelper.getDamage(sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage);
        float totalDamage = (baseDamage + enchBonus);

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
        DamageSource src = user.getDamageSources().outOfWorld();
        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;

        // Durability
        stack.damage(1, user, EquipmentSlot.MAINHAND);
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

    // == Data Classes ==
    private static class PullData {
        final UUID targetId;
        final UUID playerId;
        int ticksLeft;
        final double perTickStrength;

        PullData(UUID targetId, UUID playerId, int ticksLeft, double perTickStrength) {
            this.targetId = targetId;
            this.playerId = playerId;
            this.ticksLeft = ticksLeft;
            this.perTickStrength = perTickStrength;
        }
    }

    private static class SlashAnim {
        final UUID playerId;
        final MementoMoriItem source;
        int ticksLeft;
        final int totalTicks;
        final double radius;
        final double damageRadius;
        final double baseAngle;
        int startDelay;
        boolean aoeDone = false;

        SlashAnim(UUID playerId, MementoMoriItem source,
                  int totalTicks, int startDelay, double radius,
                  double damageRadius, double baseAngle) {
            this.playerId = playerId;
            this.source = source;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.startDelay = startDelay;
            this.radius = radius;
            this.damageRadius = damageRadius;
            this.baseAngle = baseAngle;
        }
    }

    private static class PullVisual {
        final UUID playerId;
        int ticksLeft;
        final List<Vec3d> particles;

        PullVisual(UUID playerId, int ticksLeft, List<Vec3d> particles) {
            this.playerId = playerId;
            this.ticksLeft = ticksLeft;
            this.particles = particles;
        }
    }
}
