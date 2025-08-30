package com.lycoris.modid.item.custom.dualblades;

import com.lycoris.modid.component.ModDataComponentTypes;
import com.lycoris.modid.util.CooldownBarManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LucidityItem extends SwordItem {

    // Skill Names
    private static final String SKILL_NAME = "Pursuit";
    private static final String ULT_NAME   = "Lucid Eclipse";

    // Maps
    private static final Map<UUID, DashData> PLAYER_DASH = new ConcurrentHashMap<>();
    private static final Map<UUID, SlashAnim> ACTIVE_SLASHES = new ConcurrentHashMap<>();
    private static final Map<UUID, EclipseAnim> ACTIVE_ECLIPSE = new ConcurrentHashMap<>();
    private static final Map<UUID, EclipseSeq> ECLIPSE_SEQUENCE = new ConcurrentHashMap<>();

    // Server Tick Handling
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {

            // --Handle Cooldown
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ItemStack held = player.getMainHandStack();
                if (held.getItem() instanceof LucidityItem) {
                    int skillCd = held.getOrDefault(ModDataComponentTypes.SKILL_COOLDOWN, 0);
                    if (skillCd > 0) {
                        held.set(ModDataComponentTypes.SKILL_COOLDOWN, skillCd - 1);
                    }
                    int ultCd = held.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);
                    if (ultCd > 0) {
                        held.set(ModDataComponentTypes.ULTIMATE_COOLDOWN, ultCd - 1);
                    }

                    CooldownBarManager.updateSkillBar(player, SKILL_NAME, skillCd, 20 * 5); // 5s
                    CooldownBarManager.updateUltimateBar(player, ULT_NAME, ultCd, 20 * 45); // 45s

                } else {
                    CooldownBarManager.removeBar(player, SKILL_NAME);
                    CooldownBarManager.removeBar(player, ULT_NAME);
                }
            }

            // --Handle Dash
            for (Iterator<Map.Entry<UUID, DashData>> it = PLAYER_DASH.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, DashData> entry = it.next();
                DashData dash = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

                if (player != null) {
                    Vec3d velocity = player.getVelocity();
                    boolean stillMovingFast = velocity.horizontalLength() > 0.13; // sprint cutoff

                    if (dash.ticksLeft > 0 || stillMovingFast) {
                        if (dash.ticksLeft > 0) {
                            Vec3d step = dash.direction.multiply(dash.speed);
                            player.addVelocity(step.x, step.y * 0.1, step.z);
                            player.velocityModified = true;
                            dash.ticksLeft--;
                        }
                    } else {
                        if (dash.onComplete != null) {
                            dash.onComplete.accept(player);
                        }
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }

            // --Handle Slash Effect
            for (Iterator<Map.Entry<UUID, SlashAnim>> it = ACTIVE_SLASHES.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, SlashAnim> e = it.next();
                SlashAnim anim = e.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player == null || !player.isAlive()) { it.remove(); continue; }

                ServerWorld sw = player.getServerWorld();

                if (!anim.aoeDone) {
                    double aoeRadius = anim.damageRadius;
                    List<LivingEntity> aoeTargets = sw.getEntitiesByClass(
                            LivingEntity.class,
                            player.getBoundingBox().expand(aoeRadius),
                            t -> t.isAlive() && t != player
                    );
                    for (LivingEntity t : aoeTargets) {
                        attackWithFullDamage(player, t);
                    }
                    anim.aoeDone = true;
                }

                int tickIndex = anim.totalTicks - anim.ticksLeft;
                double progress = (double) tickIndex / anim.totalTicks;
                double angle = anim.baseAngle + progress * (Math.PI * 2.0);

                double arcHalfWidth = Math.toRadians(60);
                int samples = 60;
                double y = player.getBodyY(0.5);

                for (int s = 0; s < samples; s++) {
                    double t = (s / (double)(samples - 1)) * 2.0 - 1.0;
                    double a = angle + t * arcHalfWidth;

                    for (double offset = 0; offset <= 2; offset += 0.3) {
                        double px = player.getX() + (anim.radius - 1.0 + offset) * Math.cos(a);
                        double pz = player.getZ() + (anim.radius - 1.0 + offset) * Math.sin(a);

                        if (sw.random.nextFloat() < 0.3F) {
                            sw.spawnParticles(ParticleTypes.CRIT, px, y, pz, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                    }
                }

                anim.ticksLeft--;
                if (anim.ticksLeft <= 0) it.remove();
            }

            // --Lucid Eclipse Sequence Handling
            for (Iterator<Map.Entry<UUID, EclipseSeq>> it = ECLIPSE_SEQUENCE.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, EclipseSeq> e = it.next();
                UUID id = e.getKey();
                EclipseSeq seq = e.getValue();

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null || !player.isAlive()) {
                    it.remove();
                    continue;
                }

                seq.ticks++;
                if (seq.ticks >= seq.delay) {
                    seq.ticks = 0;

                    String dir = (seq.done % 2 == 0) ? "LEFT" : "RIGHT";
                    triggerArcAttack(player, dir);

                    seq.done++;

                    // new random delay for the next swing (5–10 ticks)
                    seq.delay = 5 + player.getWorld().random.nextInt(6);

                    if (seq.done >= seq.totalHits) {
                        it.remove();
                    }
                }
            }

            // --- Handle Eclipse Arc Animations (visual only)
            for (Iterator<Map.Entry<UUID, EclipseAnim>> it = ACTIVE_ECLIPSE.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, EclipseAnim> e = it.next();
                EclipseAnim anim = e.getValue();

                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player == null || !player.isAlive()) { it.remove(); continue; }

                boolean stillAlive = animateEclipse(player, anim);
                if (!stillAlive) {
                    it.remove();
                }
            }
        });
    }

    // Skill Activation Handling
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            int skillCld = stack.getOrDefault(ModDataComponentTypes.SKILL_COOLDOWN, 0);
            if (user.isSneaking()) {
                if (!(skillCld > 0)) {
                    Pursuit(user);
                    stack.set(ModDataComponentTypes.SKILL_COOLDOWN, 20 * 5);
                } else {
                    if (user instanceof ServerPlayerEntity sp)
                        sp.sendMessage(Text.literal("Skill on Cooldown").formatted(Formatting.GRAY), true);
                }
            }
        }
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof PlayerEntity player) {
            int ultCld = stack.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);
            if (player.isSneaking()) {
                if (!(ultCld > 0)) {
                    LucidEclipse(player, player.getWorld());
                    stack.set(ModDataComponentTypes.ULTIMATE_COOLDOWN, 20 * 45);
                } else {
                    if (player instanceof ServerPlayerEntity sp)
                        sp.sendMessage(Text.literal("Ultimate on Cooldown").formatted(Formatting.DARK_GRAY), true);
                }
            }
        }
        return super.postHit(stack, target, attacker);
    }

    public LucidityItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, settings);
    }

    // Skills
    private void Pursuit(PlayerEntity user) {
        Vec3d direction = user.getRotationVec(1.0F).normalize();
        Vec3d startPos = user.getPos();

        PLAYER_DASH.put(user.getUuid(), new DashData(
                user.getUuid(),
                direction,
                startPos,
                4,
                0.5,
                player -> {
                    Vec3d look = user.getRotationVec(1.0F).normalize();
                    double baseAngle = Math.atan2(look.z, look.x);
                    ACTIVE_SLASHES.put(user.getUuid(),
                            new SlashAnim(user.getUuid(), this,
                                    4, 2.0, 2.0, baseAngle));

                    DualBladeHandler.clearLockout(user);
                    DualBladeHandler.extendComboWindow(user, Hand.MAIN_HAND, 3000);
                }
        ));
    }

    private void LucidEclipse(PlayerEntity user, World world) {
        if (!(user instanceof ServerPlayerEntity)) return;

        UUID id = user.getUuid();
        ECLIPSE_SEQUENCE.put(id, new EclipseSeq(world));
    }

    // Helpers
    private static void attackWithFullDamage(PlayerEntity user, LivingEntity target) {
        if (user.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) user.getWorld();
        ItemStack stack = user.getMainHandStack();

        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float enchBonus = EnchantmentHelper.getDamage(sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage);
        float totalDamage = baseDamage + enchBonus;

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

    private static void triggerArcAttack(PlayerEntity user, String dir) {
        if (!(user.getWorld() instanceof ServerWorld sw)) return;

        double attackRadius = 6.0;
        double coneDeg = 120.0;
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
                Vec3d dirToTarget = toTarget.normalize();
                double dot = dirToTarget.dotProduct(look);
                if (dot >= coneCos) {
                    attackWithFullDamage(user, target);
                }
            }
        }

        spawnEclipseArc(user, dir);

        Vec3d dash = look.multiply(0.25);
        user.addVelocity(dash.x, 0, dash.z);
        user.velocityModified = true;
    }

    private static void spawnEclipseArc(PlayerEntity user, String dir) {
        if (!(user.getWorld() instanceof ServerWorld sw)) return;

        double baseYaw = Math.toRadians(user.getYaw());
        double randomTilt = Math.toRadians(-45 + sw.random.nextInt(91));

        double sweepRange = Math.PI;
        double offset = Math.toRadians(90);
        double start = -sweepRange / 2 + offset;
        double end = +sweepRange / 2 + offset;

        if ("LEFT".equals(dir)) {
            double tmp = start;
            start = end;
            end = tmp;
        }

        ACTIVE_ECLIPSE.put(user.getUuid(),
                new EclipseAnim(start, end, 3.5, 5, randomTilt, baseYaw));
    }

    private static boolean animateEclipse(PlayerEntity player, EclipseAnim anim) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;
        if (!player.isAlive()) return false;

        int tickIndex = anim.totalTicks - anim.ticksLeft;
        double progress = (double) tickIndex / anim.totalTicks;
        double localAngle = anim.start + (anim.end - anim.start) * progress;

        double arcHalfWidth = Math.toRadians(24);
        int samples = 48;

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

    // Data Classes
    private static class DashData {
        final UUID playerId;
        final Vec3d direction;
        final Vec3d startPos;
        int ticksLeft;
        final double speed;
        final Consumer<ServerPlayerEntity> onComplete;

        DashData(UUID playerId, Vec3d direction, Vec3d startPos, int ticksLeft, double speed,
                 Consumer<ServerPlayerEntity> onComplete) {
            this.playerId = playerId;
            this.direction = direction;
            this.startPos = startPos;
            this.ticksLeft = ticksLeft;
            this.speed = speed;
            this.onComplete = onComplete;
        }
    }

    private static class SlashAnim {
        final UUID playerId;
        final LucidityItem source;
        int ticksLeft;
        final int totalTicks;
        final double radius;
        final double damageRadius;
        final double baseAngle;
        boolean aoeDone = false;

        SlashAnim(UUID playerId, LucidityItem source,
                  int totalTicks, double radius,
                  double damageRadius, double baseAngle) {
            this.playerId = playerId;
            this.source = source;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.radius = radius;
            this.damageRadius = damageRadius;
            this.baseAngle = baseAngle;
        }
    }

    public static class EclipseAnim {
        public final double start;
        public final double end;
        public final double radius;
        public final int totalTicks;
        public int ticksLeft;
        public final double tilt;
        public final double baseYaw;

        public EclipseAnim(double start, double end, double radius, int totalTicks, double tilt, double baseYaw) {
            this.start = start;
            this.end = end;
            this.radius = radius;
            this.totalTicks = totalTicks;
            this.ticksLeft = totalTicks;
            this.tilt = tilt;
            this.baseYaw = baseYaw;
        }
    }

    public static class EclipseSeq {
        int totalHits = 27;
        int delay;
        int ticks = 0;
        int done = 0;

        EclipseSeq(World world) {
            this.delay = 5 + world.random.nextInt(6);
        }
    }

}
