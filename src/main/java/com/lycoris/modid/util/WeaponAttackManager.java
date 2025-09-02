package com.lycoris.modid.util;

import com.lycoris.modid.effect.ModEffects;
import com.lycoris.modid.item.custom.MementoMoriItem;
import com.lycoris.modid.item.custom.SanguineSwordItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WeaponAttackManager {

    // === Registries ===
    private static final Map<UUID, DashData> ACTIVE_DASHES = new ConcurrentHashMap<>();
    private static final Map<UUID, SpinData> ACTIVE_SPINS = new ConcurrentHashMap<>();
    private static final Map<UUID, PullData> ACTIVE_PULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ArcData>> ACTIVE_ARCS = new ConcurrentHashMap<>();

    // === Server Tick Handler
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickDash(server);
            tickSpin(server);
            tickArc(server);
            tickPull(server);
        });
    }

    // === Public API ===
    public static void startDash(ServerPlayerEntity user, Vec3d direction, Vec3d startPos, int ticksLeft, double speed, List<ParticleConfig> particles,
                          Consumer<ServerPlayerEntity> onComplete) {
        ACTIVE_DASHES.put(user.getUuid(), new DashData(
                user.getUuid(),
                direction,
                startPos,
                ticksLeft,
                speed,
                particles,
                onComplete
        ));
    }

    public static void startSpin(ServerPlayerEntity user, int totalTicks, int startDelay, double radius, double baseAngle, DamageSource dmgsrc,float[] offsets, List<ParticleConfig> particles) {
        ACTIVE_SPINS.put(user.getUuid(), new SpinData(
                user.getUuid(),
                totalTicks,
                startDelay,
                radius,
                baseAngle,
                dmgsrc,
                offsets,
                particles
        ));
    }

    public static void startArc(){

    }

    // === Animation Handlers ===
    private static void tickDash(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, DashData>> it = ACTIVE_DASHES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, DashData> entry = it.next();
            DashData dash = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

            if (player != null) {
                Vec3d velocity = player.getVelocity();
                boolean stillMovingFast = velocity.horizontalLength() > 0.13; // sprint cutoff

                if (dash.ticksLeft > 0 || stillMovingFast) {
                    // Apply dash push only if timer still running
                    if (dash.ticksLeft > 0) {
                        Vec3d step = dash.direction.multiply(dash.speed);
                        player.addVelocity(step.x, step.y * 0.1, step.z);
                        player.velocityModified = true;
                        dash.ticksLeft--;
                    }

                    // Particle trail while timer running OR player still fast
                    ServerWorld sw = player.getServerWorld();
                    Box box = player.getBoundingBox().expand(0.2);

                    for (ParticleConfig cfg : dash.particles) {
                        spawnBoxParticles(sw, cfg, box, cfg.count);
                    }

                } else {
                    // Dash is fully done → trigger callback once
                    if (dash.onComplete != null) {
                        dash.onComplete.accept(player);
                    }
                    it.remove();
                }
            } else {
                it.remove(); // remove if player not found
            }
        }
    }

    private static void tickSpin(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, SpinData>> it = ACTIVE_SPINS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, SpinData> e = it.next();
            SpinData anim = e.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            ServerWorld sw = player.getServerWorld();
            // Wait for start delay
            if (anim.startDelay > 0) {
                anim.startDelay--;
                continue;
            }

            // Damage
            if (!anim.aoeDone) {
                List<LivingEntity> aoeTargets = sw.getEntitiesByClass(
                        LivingEntity.class,
                        player.getBoundingBox().expand(anim.radius),
                        t -> t.isAlive() && t != player
                );
                for (LivingEntity t : aoeTargets) {
                    dealDamage(player, t, 1.0f, anim.dmgSource);
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
                double t = (s / (double) (samples - 1)) * 2.0 - 1.0;
                double a = angle + t * arcHalfWidth;

                for (double offset = anim.offsets[0]; offset <= anim.offsets[1]; offset += anim.offsets[2]) {
                    double px = player.getX() + (anim.radius - 1.0 + offset) * Math.cos(a);
                    double pz = player.getZ() + (anim.radius - 1.0 + offset) * Math.sin(a);

                    double[] xyz = {px, y, pz};
                    for (ParticleConfig cfg : anim.particles) {
                        cfg.spawnParticles(sw, xyz, 0.1f);
                    }
                }
            }
            anim.ticksLeft--;
            if (anim.ticksLeft <= 0) it.remove();
        }
    }

    private static void tickArc(MinecraftServer server) {

    }

    private static void tickPull(MinecraftServer server) {

    }

    // === Helper Methods ===
    private static void spawnBoxParticles(ServerWorld sw, ParticleConfig cfg, Box box, int count) {
        for (int i = 0; i < count; i++) {
            double x = box.minX + sw.getRandom().nextDouble() * (box.maxX - box.minX);
            double y = box.minY + sw.getRandom().nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + sw.getRandom().nextDouble() * (box.maxZ - box.minZ);

            double[] xyz = {x, y, z};
            cfg.spawnParticles(sw, xyz, 0.0f);
        }
    }

    private static void dealDamage(PlayerEntity user, LivingEntity target, float multiplier, DamageSource src) {
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

        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;

        stack.damage(1, user, EquipmentSlot.MAINHAND);
    }

    // === Data Classes ===
    private static class DashData {
        final UUID playerId;
        final Vec3d direction;
        final Vec3d startPos;
        int ticksLeft;
        final double speed;
        List<ParticleConfig> particles;
        final Consumer<ServerPlayerEntity> onComplete;

        DashData(UUID playerId, Vec3d direction, Vec3d startPos, int ticksLeft, double speed, List<ParticleConfig> particles,
                 Consumer<ServerPlayerEntity> onComplete) {
            this.playerId = playerId;
            this.direction = direction;
            this.startPos = startPos;
            this.ticksLeft = ticksLeft;
            this.speed = speed;
            this.particles = particles;
            this.onComplete = onComplete;
        }
    }
    private static class SpinData {
        final UUID playerId;
        int ticksLeft;
        final int totalTicks;
        final double radius;
        final float[] offsets;
        List<ParticleConfig> particles;
        final double baseAngle;
        int startDelay;
        boolean aoeDone = false;
        DamageSource dmgSource;


        SpinData(UUID playerId, int totalTicks, int startDelay, double radius, double baseAngle, DamageSource dmgsrc, float[] offsets, List<ParticleConfig> particles) {
            this.playerId = playerId;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.startDelay = startDelay;
            this.radius = radius;
            this.dmgSource = dmgsrc;
            this.offsets = offsets;
            this.particles = particles;
            this.baseAngle = baseAngle;
        }
    }
    private static class ArcData {
        public final double start;
        public final double end;
        public final double radius;
        public final int totalTicks;
        public int ticksLeft;
        public final double tilt;
        public final double baseYaw;
        public int startDelay;

        public ArcData(double start, double end, double radius, int totalTicks, double tilt, double baseYaw, int startDelay) {
            this.start = start;
            this.end = end;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.tilt = tilt;
            this.baseYaw = baseYaw;
            this.startDelay = startDelay;
        }
    }
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

    public static class ParticleConfig {
        public final ParticleEffect effect;
        public final float chance;   // 0.0–1.0
        public final double spread;  // random offset
        public final int count;      // number per spawn

        public ParticleConfig(ParticleEffect effect, float chance, double spread, int count) {
            this.effect = effect;
            this.chance = chance;
            this.spread = spread;
            this.count = count;
        }

        private void spawnParticles(ServerWorld sw, double[] loc, float speed) {
            if (sw.random.nextFloat() < this.chance) sw.spawnParticles(this.effect, loc[0], loc[1], loc[2], this.count, this.spread, this.spread, this.spread, speed);
        }
    }
}
