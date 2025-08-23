package com.lycoris.modid.item.custom;

import com.lycoris.modid.component.ModDataComponentTypes;
import com.lycoris.modid.sound.ModSounds;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SanguineSwordItem extends SwordItem {

    // === Runtime State ===
    private static final Map<UUID, DashData> PLAYER_DASH = new ConcurrentHashMap<>();
    private static final Map<UUID, DelayedExplosion> SKILL_THREE_DELAYED_ATTACK = new ConcurrentHashMap<>();
    private static final Map<UUID, PullData> ENTITY_PULL = new ConcurrentHashMap<>();

    // === Ticking System ===
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // --- Handle dash movement + trail ---
            for (Iterator<Map.Entry<UUID, DashData>> it = PLAYER_DASH.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, DashData> entry = it.next();
                DashData dash = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

                if (player != null) {
                    Vec3d velocity = player.getVelocity();
                    boolean stillMovingFast = velocity.horizontalLength() > 0.13; // sprinting cutoff

                    if (dash.ticksLeft > 0 || stillMovingFast) {
                        if (dash.ticksLeft > 0) {
                            Vec3d step = dash.direction.multiply(dash.speed);
                            player.addVelocity(step.x, step.y * 0.1, step.z);
                            player.velocityModified = true;
                            dash.ticksLeft--;
                        }

                        // Particle streak matching player's body
                        ServerWorld sw = player.getServerWorld();
                        Box box = player.getBoundingBox().expand(0.1);
                        spawnBoxParticles(
                                sw,
                                new DustParticleEffect(new Vector3f(0.9F, 0.0F, 0.0F), 2.2F),
                                box,
                                25, // density
                                0.05
                        );
                    } else {
                        it.remove();
                    }
                } else {
                    it.remove();
                }
            }

            // --- Handle delayed explosion from skill three ---
            for (Iterator<Map.Entry<UUID, DelayedExplosion>> it = SKILL_THREE_DELAYED_ATTACK.entrySet().iterator(); it.hasNext();) {
                Map.Entry<UUID, DelayedExplosion> entry = it.next();
                DelayedExplosion exp = entry.getValue();

                if (--exp.ticksLeft <= 0) {
                    ServerPlayerEntity sp = server.getPlayerManager().getPlayer(exp.playerId);
                    if (sp != null && sp.isAlive()) {
                        ServerWorld sw = sp.getServerWorld();

                        // Explosion SFX
                        sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                                ModSounds.SANGUINE_EXPLOSION, SoundCategory.PLAYERS,
                                2.0F, 0.8F);

                        // Huge red explosion particles
                        sw.spawnParticles(
                                new DustParticleEffect(new Vector3f(0.7F, 0.0F, 0.0F), 3.0F),
                                sp.getX(), sp.getBodyY(0.5), sp.getZ(),
                                2000, 10, 10, 10, 0.5
                        );

                        // AoE damage
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

            // --- Handle pull effect from skill three ---
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

    // === Charge Helpers ===
    public static int getCharges(ItemStack stack) {
        return stack.getOrDefault(ModDataComponentTypes.SANGUINE_CHARGES, 0);
    }
    public static void setCharges(ItemStack stack, int charges) {
        stack.set(ModDataComponentTypes.SANGUINE_CHARGES, Math.max(0, Math.min(3, charges)));
    }
    public static void addCharge(ItemStack stack) { setCharges(stack, getCharges(stack) + 1); }
    public static void resetCharges(ItemStack stack) { setCharges(stack, 0); }

    // === Use Handling ===
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            int charges = getCharges(stack);

            if (!user.isSneaking()) {
                // Normal right click → add charge
                if (charges < 3) {
                    user.damage(world.getDamageSources().outOfWorld(), 4.0F);
                    addCharge(stack);
                    if (user instanceof ServerPlayerEntity sp) {
                        sp.sendMessage(Text.literal("Sanguine Soliloquy charged: " + getCharges(stack)).formatted(Formatting.RED), true);
                    }
                }
            } else {
                // Shift right click → unleash skill
                if (charges == 1) {
                    skillOne_BleedingDash(world, user);
                    user.getItemCooldownManager().set(this, 100);
                } else if (charges == 2) {
                    skillTwo_BleedingDash(world, user);
                    user.getItemCooldownManager().set(this, 200);
                } else if (charges == 3) {
                    skillThree_BleedingDash(world, user);
                    user.getItemCooldownManager().set(this, 600);
                }
                if (charges > 0) resetCharges(stack);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    // === Skills ===
    private void skillOne_BleedingDash(World world, PlayerEntity user) {
        if (user instanceof ServerPlayerEntity sp)
            sp.sendMessage(Text.literal("Blood Arts: Hemorrhage").formatted(Formatting.RED), true);

        Vec3d look = user.getRotationVec(1.0F).normalize();
        PLAYER_DASH.put(user.getUuid(), new DashData(look, user.getPos(), 4, 1.5));

        spawnParticles(world, user);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.SANGUINE_DASH, SoundCategory.PLAYERS);

        // Apply after dash finishes
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(user.getUuid());
            DashData dash = PLAYER_DASH.get(user.getUuid());
            if (player != null && dash != null && dash.ticksLeft == 0) {
                double traveled = player.getPos().distanceTo(dash.startPos);
                Box dashBox = player.getBoundingBox().stretch(dash.direction.multiply(traveled)).expand(1.5);

                for (LivingEntity target : player.getWorld().getEntitiesByClass(LivingEntity.class, dashBox, e -> e.isAlive() && e != player)) {
                    attackWithMultiplier(player, target, 1.0f);
                    spawnSweepParticle(player.getWorld(), target);
                }
            }
        });
    }

    private void skillTwo_BleedingDash(World world, PlayerEntity user) {
        if (user instanceof ServerPlayerEntity sp)
            sp.sendMessage(Text.literal("Blood Arts: Paralysis").formatted(Formatting.RED), true);

        Vec3d look = user.getRotationVec(1.0F).normalize();
        PLAYER_DASH.put(user.getUuid(), new DashData(look, user.getPos(), 4, 1.5));

        spawnParticles(world, user);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.SANGUINE_DASH, SoundCategory.PLAYERS);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(user.getUuid());
            DashData dash = PLAYER_DASH.get(user.getUuid());
            if (player != null && dash != null && dash.ticksLeft == 0) {
                double traveled = player.getPos().distanceTo(dash.startPos);
                Box dashBox = player.getBoundingBox().stretch(dash.direction.multiply(traveled)).expand(3.0);

                for (LivingEntity target : player.getWorld().getEntitiesByClass(LivingEntity.class, dashBox, e -> e.isAlive() && e != player)) {
                    freezeEntity(target, 40);
                    attackWithMultiplier(player, target, 1.5f);
                    spawnSweepParticle(player.getWorld(), target);
                }
            }
        });
    }

    private void skillThree_BleedingDash(World world, PlayerEntity user) {
        if (user instanceof ServerPlayerEntity sp)
            sp.sendMessage(Text.literal("Blood Arts: Crimson Fireworks").formatted(Formatting.DARK_RED), true);

        Vec3d look = user.getRotationVec(1.0F).normalize();
        PLAYER_DASH.put(user.getUuid(), new DashData(look, user.getPos(), 4, 2.5));

        spawnParticles(world, user);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.SANGUINE_DASH, SoundCategory.PLAYERS);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(user.getUuid());
            DashData dash = PLAYER_DASH.get(user.getUuid());
            if (player != null && dash != null && dash.ticksLeft == 0) {
                double traveled = player.getPos().distanceTo(dash.startPos);
                Box dashBox = player.getBoundingBox().stretch(dash.direction.multiply(traveled)).expand(3.0);

                for (LivingEntity target : player.getWorld().getEntitiesByClass(LivingEntity.class, dashBox, e -> e.isAlive() && e != player)) {
                    attackWithMultiplier(player, target, 2.0f);
                    pullEntity(target, player);
                    spawnSweepParticle(player.getWorld(), target);
                }

                // Freeze player, then trigger delayed explosion
                freezeEntity(player, 40);
                SKILL_THREE_DELAYED_ATTACK.put(player.getUuid(), new DelayedExplosion(player.getUuid(), this, 40));
            }
        });
    }


    // === Item Name & Tooltip ===
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
        super.appendTooltip(stack, context, tooltip, type);
    }

    // === Helpers ===
    protected void attackWithMultiplier(PlayerEntity user, LivingEntity target, float multiplier) {
        if (user.getWorld().isClient) return;
        ItemStack stack = user.getMainHandStack();
        ServerWorld sw = (ServerWorld) user.getWorld();

        float baseDamage = (float) user.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        float enchBonus = EnchantmentHelper.getDamage(sw, stack, target, user.getDamageSources().playerAttack(user), baseDamage);
        float totalDamage = (baseDamage + enchBonus) * multiplier;

        ItemEnchantmentsComponent enchComp = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        RegistryEntry<Enchantment> fireAspectEntry = sw.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(Enchantments.FIRE_ASPECT);
        int fireLevel = enchComp.getLevel(fireAspectEntry);
        if (fireLevel > 0) target.setOnFireFor(4 * fireLevel);

        DamageSource src = user.getDamageSources().playerAttack(user);
        target.timeUntilRegen = 0;
        target.damage(src, totalDamage);
        target.timeUntilRegen = 0;
    }

    private void freezeEntity(LivingEntity entity, int durationTicks) {
        // Max slowness so they can't walk
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, 255, false, false, false));
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

    private void spawnParticles(World world, PlayerEntity user) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(
                    new DustParticleEffect(new Vector3f(1.0F, 0.1F, 0.1F), 2.8F),
                    user.getX(), user.getBodyY(0.5), user.getZ(),
                    120, 1.2, 1.2, 1.2, 0.1
            );
        }
    }

    private static void spawnBoxParticles(ServerWorld sw, DustParticleEffect effect, Box box, int count, double jitter) {
        for (int i = 0; i < count; i++) {
            double x = box.minX + sw.getRandom().nextDouble() * (box.maxX - box.minX);
            double y = box.minY + sw.getRandom().nextDouble() * (box.maxY - box.minY);
            double z = box.minZ + sw.getRandom().nextDouble() * (box.maxZ - box.minZ);
            sw.spawnParticles(effect, x, y, z, 1, jitter, jitter, jitter, 0.0);
        }
    }

    // === Data Classes ===
    private static class DashData {
        final Vec3d direction;
        final Vec3d startPos; // NEW: starting position for dynamic dash length
        int ticksLeft;
        final double speed;

        DashData(Vec3d direction, Vec3d startPos, int ticksLeft, double speed) {
            this.direction = direction;
            this.startPos = startPos;
            this.ticksLeft = ticksLeft;
            this.speed = speed;
        }
    }
    private static class DelayedExplosion {
        final UUID playerId; final SanguineSwordItem sword; int ticksLeft;
        DelayedExplosion(UUID playerId, SanguineSwordItem sword, int delay) { this.playerId = playerId; this.sword = sword; this.ticksLeft = delay; }
    }
    private static class PullData {
        final UUID targetId; final UUID playerId; int ticksLeft;
        PullData(UUID targetId, UUID playerId, int ticks) { this.targetId = targetId; this.playerId = playerId; this.ticksLeft = ticks; }
    }
}
