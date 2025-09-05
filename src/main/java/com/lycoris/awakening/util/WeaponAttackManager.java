package com.lycoris.awakening.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WeaponAttackManager {

    // === Registries ===
    private static final Map<UUID, DashData> ACTIVE_DASHES = new ConcurrentHashMap<>();
    private static final Map<UUID, SpinData> ACTIVE_SPINS = new ConcurrentHashMap<>();
    private static final Map<UUID, PullData> ACTIVE_PULLS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ArcData>> ACTIVE_ARCS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<SlashData>> ACTIVE_SLASHES = new ConcurrentHashMap<>();

    // === Server Tick Handler
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickDash(server);
            tickSpin(server);
            tickArc(server);
            tickPull(server);
            tickSlash(server);
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

    public static void startSpin(ServerPlayerEntity user, int totalTicks, int startDelay, double radius, double baseAngle, DamageSource dmgsrc,float[] offsets, List<ParticleConfig> particles, RegistryEntry<StatusEffect> se) {
        ACTIVE_SPINS.put(user.getUuid(), new SpinData(
                user.getUuid(),
                totalTicks,
                startDelay,
                radius,
                baseAngle,
                dmgsrc,
                offsets,
                particles,
                se));
    }

    public static void startAngledSpinFromPos(ServerPlayerEntity user, int totalTicks, int startDelay,
                                       double radius, double baseAngle, double yawDeg, double tiltDeg,
                                       DamageSource dmgsrc, float[] offsets, List<ParticleConfig> particles, Vec3d origin) {
        ACTIVE_SPINS.put(user.getUuid(), new SpinData(
                user.getUuid(), totalTicks, startDelay,
                radius, baseAngle, yawDeg, tiltDeg,
                dmgsrc, offsets, particles, origin
        ));
    }

    public static void startArc(ServerPlayerEntity user, double start, double end, boolean fromLeft, double size, int density, double radius, int totalTicks, double tilt, double baseYaw, int startDelay, List<ParticleConfig> particles) {
        ACTIVE_ARCS
                .computeIfAbsent(user.getUuid(), k -> new ArrayList<>())
                .add(new ArcData(start, end, fromLeft, size, density, radius, totalTicks, tilt, baseYaw, startDelay, particles));
    }

    public static void startMultiSlash(LivingEntity target, int totalSlashes, float slashSize, int delayBetween, List<ParticleConfig> particles,
                                       Consumer<LivingEntity> onSlash) {
        ACTIVE_SLASHES
                .computeIfAbsent(target.getUuid(), k -> new ArrayList<>())
                .add(new SlashData(target.getUuid(), totalSlashes, slashSize, delayBetween, particles, onSlash));
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
                Box dmgbox;
                if(anim.origin != null){
                    dmgbox = new Box(anim.origin, anim.origin).expand(anim.radius);
                } else {
                    dmgbox = player.getBoundingBox().expand(anim.radius);
                }
                List<LivingEntity> aoeTargets = sw.getEntitiesByClass(
                        LivingEntity.class,
                        dmgbox,
                        t -> t.isAlive() && t != player
                );
                for (LivingEntity t : aoeTargets) {
                    dealDamage(player, t, 1.0f, anim.dmgSource);
                    if(anim.statusEffect != null){
                        StatusEffectInstance prev = t.getStatusEffect(anim.statusEffect);
                        int amp = (prev == null ? 0 : prev.getAmplifier() + 1);
                        t.addStatusEffect(new StatusEffectInstance(anim.statusEffect, 200, amp, false, true));
                    }
                }
                anim.aoeDone = true;
            }

            // Cinematic spin-slash
            int tickIndex = anim.totalTicks - anim.ticksLeft;
            double progress = (double) tickIndex / anim.totalTicks;
            double angle = anim.baseAngle + progress * (Math.PI * 2.0);

            // Arc half-width same as before
            double arcHalfWidth = Math.toRadians(60);
            int samples = 60;
            double cx = anim.origin != null ? anim.origin.x : player.getX();
            double cz = anim.origin != null ? anim.origin.z : player.getZ();
            double yBase = anim.origin != null ? anim.origin.y : player.getBodyY(0.5);

            double yawRad = Math.toRadians(anim.baseYawDeg);
            double tiltRad = Math.toRadians(anim.tiltDeg);

            for (int s = 0; s < samples; s++) {
                double t = (s / (double) (samples - 1)) * 2.0 - 1.0;
                double a = angle + t * arcHalfWidth;

                for (double offset = anim.offsets[0]; offset <= anim.offsets[1]; offset += anim.offsets[2]) {
                    double lx = (anim.radius - 1.0 + offset) * Math.cos(a);
                    double lz = (anim.radius - 1.0 + offset) * Math.sin(a);
                    double ly = 0;

                    // Tilt around Z
                    double lxTilted = lx * Math.cos(tiltRad);
                    double lyTilted = lx * Math.sin(tiltRad) + ly;

                    // Yaw around Y
                    double wx = lxTilted * Math.cos(yawRad) - lz * Math.sin(yawRad);
                    double wz = lxTilted * Math.sin(yawRad) + lz * Math.cos(yawRad);
                    double wy = yBase + lyTilted;

                    double px = cx + wx;
                    double pz = cz + wz;

                    for (ParticleConfig cfg : anim.particles) {
                        cfg.spawnParticles(sw, new double[]{px, wy, pz}, 0.05f);
                    }
                }
            }

            anim.ticksLeft--;
            if (anim.ticksLeft <= 0) it.remove();
        }
    }

    private static void tickArc(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, List<ArcData>>> it = ACTIVE_ARCS.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, List<ArcData>> e = it.next();
            UUID playerId = e.getKey();
            List<ArcData> arcs = e.getValue();

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            arcs.removeIf(anim -> {
                if (anim.startDelay > 0) {
                    anim.startDelay--;
                    return false;
                }
                return !animateArc(player, anim);
            });

            if (arcs.isEmpty()) {
                it.remove();
            }
        }
    }

    private static void tickPull(MinecraftServer server) {

    }

    private static void tickSlash(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, List<SlashData>>> it = ACTIVE_SLASHES.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, List<SlashData>> e = it.next();
            UUID targetId = e.getKey();
            LivingEntity target = null;

            // Try resolving the entity in any world
            for (ServerWorld sw : server.getWorlds()) {
                var entity = sw.getEntity(targetId);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    target = living;
                    break;
                }
            }

            if (target == null) {
                it.remove();
                continue;
            }

            List<SlashData> slashes = e.getValue();
            Iterator<SlashData> slashIt = slashes.iterator();

            while (slashIt.hasNext()) {
                SlashData data = slashIt.next();

                if (data.nextSlashTick > 0) {
                    data.nextSlashTick--;
                    continue;
                }

                // Perform a slash
                data.remaining--;
                data.nextSlashTick = data.delayBetween;

                // Damage / effect callback
                if (data.onSlash != null) {
                    data.onSlash.accept(target);
                }

                // Particle effect
                spawnSlashEffect((ServerWorld) target.getWorld(), target, data);

                if (data.remaining <= 0) {
                    slashIt.remove();
                }
            }

            if (slashes.isEmpty()) it.remove();
        }
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

    private static void spawnSlashEffect(ServerWorld sw, LivingEntity target, SlashData data) {
        Vec3d center = target.getPos().add(0, target.getHeight() * 0.5, 0);

        double yaw = sw.random.nextDouble() * 2 * Math.PI;
        double pitch = (sw.random.nextDouble() - 0.5) * Math.PI / 2;

        double dx = Math.cos(pitch) * Math.cos(yaw);
        double dy = Math.sin(pitch);
        double dz = Math.cos(pitch) * Math.sin(yaw);
        Vec3d dir = new Vec3d(dx, dy, dz).normalize();

        // ✅ Directly use data.slashSize
        double halfLen = data.slashSize * 0.5;
        double step = 0.2;

        for (double t = -halfLen; t <= halfLen; t += step) {
            Vec3d pos = center.add(dir.multiply(t));
            double[] xyz = {pos.x, pos.y, pos.z};

            for (ParticleConfig cfg : data.particles) {
                cfg.spawnParticles(sw, xyz, 0.05f);
            }
        }
    }


    private static boolean animateArc(PlayerEntity player, ArcData anim) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;
        if (!player.isAlive()) return false;

        // Swap start/end if coming from left
        if (anim.fromLeft) {
            double temp = anim.end;
            anim.end = anim.start;
            anim.start = temp;
        }

        // === Fix: convert yaw + tilt to radians ===
        double yawRad = Math.toRadians(anim.baseYaw);
        double tiltRad = Math.toRadians(anim.tilt);

        int tickIndex = anim.totalTicks - anim.ticksLeft;
        double progress = (double) tickIndex / anim.totalTicks;
        double localAngle = Math.toRadians(anim.start + (anim.end - anim.start) * progress);

        double arcHalfWidth = Math.toRadians(anim.size);
        int samples = anim.density;

        double yBase = player.getBodyY(0.5);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double forwardOffset = 0.6;
        double cx = player.getX() + look.x * forwardOffset;
        double cz = player.getZ() + look.z * forwardOffset;

        for (int s = 0; s < samples; s++) {
            double t = (s / (double) (samples - 1)) * 2.0 - 1.0;
            double a = localAngle + t * arcHalfWidth;

            for (double offset = 0.2; offset <= anim.radius; offset += 0.25) {
                double lx = Math.cos(a) * offset;
                double lz = Math.sin(a) * offset;

                // Apply tilt rotation around Z axis
                double ly = lx * Math.sin(tiltRad);
                double lxTilted = lx * Math.cos(tiltRad);

                // Apply yaw rotation around Y axis
                double wx = lxTilted * Math.cos(yawRad) - lz * Math.sin(yawRad);
                double wz = lxTilted * Math.sin(yawRad) + lz * Math.cos(yawRad);
                double wy = yBase + ly;

                double px = cx + wx;
                double pz = cz + wz;

                for (ParticleConfig cfg : anim.particles) {
                    cfg.spawnParticles(sw, new double[]{px, wy, pz}, 0.05f);
                }
            }
        }

        // Debug logging
        // System.out.println("Arc tick for " + player.getName().getString() + " (remaining " + anim.ticksLeft + ")");

        anim.ticksLeft--;
        return anim.ticksLeft > 0;
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
        final List<ParticleConfig> particles;
        final double baseAngle;
        int startDelay;
        boolean aoeDone = false;
        final DamageSource dmgSource;
        RegistryEntry<StatusEffect> statusEffect;

        // NEW
        final double baseYawDeg;
        final double tiltDeg;
        final Vec3d origin;

        // Old spin constructor (defaults to no tilt)
        SpinData(UUID playerId, int totalTicks, int startDelay, double radius, double baseAngle,
                 DamageSource dmgsrc, float[] offsets, List<ParticleConfig> particles, RegistryEntry<StatusEffect> statusEffect) {
            this(playerId, totalTicks, startDelay, radius, baseAngle, 0, 0, dmgsrc, offsets, particles, null);
            this.statusEffect = statusEffect;
        }

        // New angled spin constructor
        SpinData(UUID playerId, int totalTicks, int startDelay, double radius, double baseAngle,
                 double baseYawDeg, double tiltDeg,
                 DamageSource dmgsrc, float[] offsets, List<ParticleConfig> particles, Vec3d origin) {
            this.playerId = playerId;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.startDelay = startDelay;
            this.radius = radius;
            this.baseAngle = baseAngle;
            this.baseYawDeg = baseYawDeg;
            this.tiltDeg = tiltDeg;
            this.dmgSource = dmgsrc;
            this.offsets = offsets;
            this.particles = particles;
            this.origin = origin;
        }
    }
    private static class ArcData {
        public double start;
        public double end;
        public final double radius;
        public final int totalTicks;
        public int ticksLeft;
        public final double tilt;
        public final double baseYaw;
        public int startDelay;
        public double size;
        public int density;
        public List<ParticleConfig> particles;
        public boolean fromLeft;

        public ArcData(double start, double end, boolean fromLeft, double size, int density, double radius, int totalTicks, double tilt, double baseYaw, int startDelay, List<ParticleConfig> particles) {
            this.start = start;
            this.end = end;
            this.fromLeft = fromLeft;
            this.size = size;
            this.density = density;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.tilt = tilt;
            this.baseYaw = baseYaw;
            this.startDelay = startDelay;
            this.particles = particles;
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
    private static class SlashData {
        final UUID entityId;                // target to slash
        final int totalSlashes;             // e.g. 3
        final float slashSize;
        final int delayBetween;             // ticks between slashes
        int nextSlashTick;                  // ticks until next slash
        int remaining;                      // how many slashes left
        List<ParticleConfig> particles;
        final Consumer<LivingEntity> onSlash;

        SlashData(UUID entityId, int totalSlashes, float slashSize, int delayBetween, List<ParticleConfig> particles, Consumer<LivingEntity> onSlash) {
            this.entityId = entityId;
            this.totalSlashes = totalSlashes;
            this.slashSize = slashSize;
            this.delayBetween = delayBetween;
            this.remaining = totalSlashes;
            this.nextSlashTick = 0; // first slash happens immediately
            this.particles = particles;
            this.onSlash = onSlash;
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
