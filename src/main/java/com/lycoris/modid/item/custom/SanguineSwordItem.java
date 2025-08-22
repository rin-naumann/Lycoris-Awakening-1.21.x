package com.lycoris.modid.item.custom;

import com.lycoris.modid.component.ModDataComponentTypes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SanguineSwordItem extends SwordItem {

    private static final Map<UUID, DashData> PLAYER_DASH = new ConcurrentHashMap<>();
    private static final Map<UUID, DelayedExplosion> SKILL_THREE_DELAYED_ATTACK = new ConcurrentHashMap<>();
    private static final Map<UUID, PullData> ENTITY_PULL = new ConcurrentHashMap<>();

    // --- Tick Logic ---
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Handle dashes
            for (Iterator<Map.Entry<UUID, DashData>> it = PLAYER_DASH.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, DashData> entry = it.next();
                DashData dash = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

                if (player != null) {
                    Vec3d velocity = player.getVelocity();

                    // Sprinting speed cutoff (~0.13) - only trail if dashing/momentum > sprint
                    boolean stillMovingFast = velocity.horizontalLength() > 0.13;

                    if (dash.ticksLeft > 0 || stillMovingFast) {
                        // Apply dash velocity only while ticksLeft > 0
                        if (dash.ticksLeft > 0) {
                            Vec3d step = dash.direction.multiply(dash.speed);
                            player.addVelocity(step.x, step.y * 0.1, step.z);
                            player.velocityModified = true;
                            dash.ticksLeft--;
                        }

                        // Spawn silhouette trail that matches player's hitbox
                        ServerWorld sw = player.getServerWorld();
                        Box box = player.getBoundingBox().expand(0.1);
                        spawnBoxParticles(
                                sw,
                                new DustParticleEffect(new Vector3f(0.9F, 0.0F, 0.0F), 2.2F),
                                box,
                                20, // number of particles
                                0.05 // motion spread
                        );
                    } else {
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }

            // Handle delayed explosions (unchanged)
            for (Iterator<Map.Entry<UUID, DelayedExplosion>> it = SKILL_THREE_DELAYED_ATTACK.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, DelayedExplosion> entry = it.next();
                DelayedExplosion exp = entry.getValue();

                if (--exp.ticksLeft <= 0) {
                    ServerPlayerEntity sp = server.getPlayerManager().getPlayer(exp.playerId);
                    if (sp != null && sp.isAlive()) {
                        ServerWorld sw = sp.getServerWorld();

                        // Big red explosion
                        sw.spawnParticles(
                                new DustParticleEffect(new Vector3f(0.7F, 0.0F, 0.0F), 3.0F),
                                sp.getX(), sp.getBodyY(0.5), sp.getZ(),
                                2000, 10, 10, 10, 0.5
                        );

                        // Deal AoE damage
                        List<LivingEntity> aoeTargets = sw.getEntitiesByClass(
                                LivingEntity.class,
                                sp.getBoundingBox().expand(10.0),
                                e -> e.isAlive() && e != sp
                        );

                        for (LivingEntity target : aoeTargets) {
                            exp.sword.attackWithMultiplier(sp, target, 3.0f);
                            exp.sword.freezeEntity(target, 60);
                            exp.sword.spawnSweepParticle(sw, target);
                        }
                    }
                    it.remove();
                }
            }

            // Handle pulling entities (unchanged)
            for (Iterator<Map.Entry<UUID, PullData>> it = ENTITY_PULL.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, PullData> entry = it.next();
                PullData pullData = entry.getValue();
                ServerWorld sw = server.getOverworld();
                LivingEntity target = (LivingEntity) sw.getEntity(entry.getKey());
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(pullData.playerId);

                if (target != null && player != null && target.isAlive() && player.isAlive() && pullData.ticksLeft > 0) {
                    Vec3d pull = player.getPos().subtract(target.getPos()).normalize().multiply(0.6);
                    target.addVelocity(pull.x, 0.05, pull.z);
                    target.velocityModified = true;
                    pullData.ticksLeft--;
                } else {
                    it.remove();
                }
            }
        });
    }

    public SanguineSwordItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    // --- Charge Helpers ---
    public static int getCharges(ItemStack stack) {
        return stack.getOrDefault(ModDataComponentTypes.SANGUINE_CHARGES, 0);
    }

    public static void setCharges(ItemStack stack, int charges) {
        stack.set(ModDataComponentTypes.SANGUINE_CHARGES, Math.max(0, Math.min(3, charges)));
    }

    public static void addCharge(ItemStack stack) {
        setCharges(stack, getCharges(stack) + 1);
    }

    public static void resetCharges(ItemStack stack) {
        setCharges(stack, 0);
    }

    // --- Right Click Handling ---
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            int charges = getCharges(stack);

            if (!user.isSneaking()) {
                // Normal right click -> add charge (max 3)
                if (charges < 3) {
                    user.damage(world.getDamageSources().outOfWorld(), 4.0F); // 2 hearts self-damage
                    addCharge(stack);
                    if (user instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(Text.literal("Sanguine Soliloquy charged: " + getCharges(stack)).formatted(Formatting.RED), true);
                    }
                }
            } else {
                // Shift right click -> unleash skill
                if (charges == 1) {
                    skillOne_BleedingDash(world, user, stack);
                    user.getItemCooldownManager().set(this, 100); // 5s
                } else if (charges == 2) {
                    skillTwo_BleedingDash(world, user, stack);
                    user.getItemCooldownManager().set(this, 200); // 10s
                } else if (charges == 3) {
                    skillThree_BleedingDash(world, user, stack);
                    user.getItemCooldownManager().set(this, 600); // 30s
                }
                if (charges > 0) resetCharges(stack);
            }
        }

        return TypedActionResult.success(stack, world.isClient);
    }

    // --- Skills ---
    private void skillOne_BleedingDash(World world, PlayerEntity user, ItemStack stack) {
        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Blood Arts (血の術): Hemorrhage (出血)").formatted(Formatting.RED), true);

        Vec3d look = user.getRotationVec(1.0F).normalize();
        PLAYER_DASH.put(user.getUuid(), new DashData(look, 4, 1.5));

        Box dashBox = user.getBoundingBox().stretch(look.multiply(10)).expand(1.5);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, dashBox, e -> e.isAlive() && e != user);

        for (LivingEntity target : targets) {
            attackWithMultiplier(user, target, 1.0f);
            spawnSweepParticle(world, target);
        }
    }

    private void skillTwo_BleedingDash(World world, PlayerEntity user, ItemStack stack) {
        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Blood Arts (血の術): Paralysis (麻痺)").formatted(Formatting.RED), true);

        Vec3d look = user.getRotationVec(1.0F).normalize();
        PLAYER_DASH.put(user.getUuid(), new DashData(look, 4, 1.5));

        Box dashBox = user.getBoundingBox().stretch(look.multiply(10)).expand(3);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, dashBox, e -> e.isAlive() && e != user);

        for (LivingEntity target : targets) {
            freezeEntity(target, 40);
            attackWithMultiplier(user, target, 1.5f);
            spawnSweepParticle(world, target);
        }
    }

    private void skillThree_BleedingDash(World world, PlayerEntity user, ItemStack stack) {
        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Blood Arts (血の術): Crimson Fireworks (真紅の花火)").formatted(Formatting.DARK_RED), true);

        Vec3d look = user.getRotationVec(1.0F).normalize();
        PLAYER_DASH.put(user.getUuid(), new DashData(look, 5, 2.5));

        Box dashBox = user.getBoundingBox().stretch(look.multiply(8)).expand(2.0);
        List<LivingEntity> dashTargets = world.getEntitiesByClass(LivingEntity.class, dashBox, e -> e.isAlive() && e != user);

        for (LivingEntity target : dashTargets) {
            attackWithMultiplier(user, target, 2.0f);
            pullEntity(target, user);
            spawnSweepParticle(world, target);
        }

        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, false, false));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 128, false, false, false));

        SKILL_THREE_DELAYED_ATTACK.put(user.getUuid(), new DelayedExplosion(user.getUuid(), this, 40));
    }

    // --- Item Name and Tooltip ---
    @Override
    public Text getName(ItemStack stack) {
        return super.getName(stack).copy().formatted(Formatting.DARK_RED);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int charges = getCharges(stack);
        tooltip.add(Text.literal("Charges: " + charges + "/3")
                .formatted(charges == 3 ? Formatting.DARK_RED : Formatting.RED));
        super.appendTooltip(stack, context, tooltip, type);
    }

    // --- Helpers ---
    protected void attackWithMultiplier(PlayerEntity user, LivingEntity target, float multiplier) {
        if (user.getWorld().isClient) return;

        ItemStack stack = user.getMainHandStack();
        ServerWorld sw = (ServerWorld) user.getWorld();

        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);

        float enchBonus = EnchantmentHelper.getDamage(
                sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage
        );

        float totalDamage = (baseDamage + enchBonus) * multiplier;

        ItemEnchantmentsComponent enchComp = stack.getOrDefault(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantmentsComponent.DEFAULT
        );
        RegistryEntry<Enchantment> fireAspectEntry =
                sw.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.FIRE_ASPECT);
        int fireLevel = enchComp.getLevel(fireAspectEntry);
        if (fireLevel > 0) {
            target.setOnFireFor(4 * fireLevel);
        }

        DamageSource src = user.getDamageSources().playerAttack(user);

        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;
    }

    protected void freezeEntity(LivingEntity entity, int durationTicks) {
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, 255, false, false, false));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, durationTicks, 128, false, false, false));
    }

    protected void pullEntity(LivingEntity target, PlayerEntity player) {
        ENTITY_PULL.put(target.getUuid(), new PullData(target.getUuid(), player.getUuid(), 10));
    }

    protected void spawnSweepParticle(World world, LivingEntity target) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(
                    new DustParticleEffect(new Vector3f(0.9F, 0.0F, 0.0F), 2.5F),
                    target.getX(), target.getBodyY(0.5), target.getZ(),
                    30, 0.8, 0.8, 0.8, 0.0
            );
        }
    }

    // Particle helper for streaks
    private static void spawnBoxParticles(ServerWorld sw, DustParticleEffect effect,
                                          Box box, int count, double jitter) {
        for (int i = 0; i < count; i++) {
            double x = box.minX + sw.getRandom().nextDouble() * (box.maxX - box.minX);
            double y = box.minY + sw.getRandom().nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + sw.getRandom().nextDouble() * (box.maxZ - box.minZ);

            sw.spawnParticles(effect,
                    x, y, z,
                    1,
                    jitter, jitter, jitter,
                    0.0
            );
        }
    }

    // --- Data Classes ---
    private static class DashData {
        final Vec3d direction;
        int ticksLeft;
        final double speed;
        DashData(Vec3d direction, int ticksLeft, double speed) {
            this.direction = direction;
            this.ticksLeft = ticksLeft;
            this.speed = speed;
        }
    }

    private static class DelayedExplosion {
        final UUID playerId;
        final SanguineSwordItem sword;
        int ticksLeft;
        DelayedExplosion(UUID playerId, SanguineSwordItem sword, int delay) {
            this.playerId = playerId;
            this.sword = sword;
            this.ticksLeft = delay;
        }
    }

    private static class PullData {
        final UUID targetId;
        final UUID playerId;
        int ticksLeft;
        PullData(UUID targetId, UUID playerId, int ticks) {
            this.targetId = targetId;
            this.playerId = playerId;
            this.ticksLeft = ticks;
        }
    }
}
