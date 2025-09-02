package com.lycoris.modid.item.custom;

import com.lycoris.modid.component.ModDataComponentTypes;
import com.lycoris.modid.sound.ModSounds;
import com.lycoris.modid.util.CooldownBarManager;
import com.lycoris.modid.util.WeaponAttackManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

    // === Skill Names ===
    private static final String SKILL_NAME = "Bleeding Dash";
    private static final String ULT_NAME   = "Crimson Fireworks";

    // === Runtime State ===
    private static final Map<UUID, DelayedExplosion> SKILL_THREE_DELAYED_ATTACK = new ConcurrentHashMap<>();
    private static final Map<UUID, PullData> ENTITY_PULL = new ConcurrentHashMap<>();

    // === Ticking System ===
    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // --- Handle cooldown system ---
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ItemStack held = player.getMainHandStack();
                if (held.getItem() instanceof SanguineSwordItem) {
                    int skillCd = held.getOrDefault(ModDataComponentTypes.SKILL_COOLDOWN, 0);
                    if (skillCd > 0) {
                        held.set(ModDataComponentTypes.SKILL_COOLDOWN, skillCd - 1);
                    }
                    int ultCd = held.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);
                    if (ultCd > 0) {
                        held.set(ModDataComponentTypes.ULTIMATE_COOLDOWN, ultCd - 1);
                    }

                    CooldownBarManager.updateSkillBar(player, SKILL_NAME, skillCd, 20 * 10);
                    CooldownBarManager.updateUltimateBar(player, ULT_NAME, ultCd, 20 * 30);

                } else {
                    CooldownBarManager.removeBar(player, SKILL_NAME);
                    CooldownBarManager.removeBar(player, ULT_NAME);

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
                            exp.sword.spawnParticles(sw, target);
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

            if (!user.isSneaking()) { // Normal right click to add charge
                if (charges < 3) {
                    if (!(user.getHealth() <= 4)){ // Checks if you have enough health.
                        int ultCld = stack.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);
                        if (!(ultCld > 0 && charges == 2)){
                            user.damage(world.getDamageSources().outOfWorld(), 4.0F);
                            addCharge(stack);
                            if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Sanguine Soliloquy charged: " + getCharges(stack)).formatted(Formatting.RED), true);
                        } else {
                            if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Ult is on Cooldown. Charges are limited." + getCharges(stack)).formatted(Formatting.RED), true);
                        }
                    } else {
                        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Not enough Health to Sacrifice").formatted(Formatting.RED), true);
                    }
                }
            } else { // Shift right click → unleash skill

                int skillCld = stack.getOrDefault(ModDataComponentTypes.SKILL_COOLDOWN, 0);
                int ultCld = stack.getOrDefault(ModDataComponentTypes.ULTIMATE_COOLDOWN, 0);
                boolean skillCasted = false;

                if (charges == 1) {
                    if (!(skillCld > 0)){
                        skillOne_BleedingDash(world, user);
                        stack.set(ModDataComponentTypes.SKILL_COOLDOWN, 20 * 5); // 5s
                        skillCasted = true;
                    } else {
                        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Skill on Cooldown").formatted(Formatting.GRAY), true);
                    }
                } else if (charges == 2) {
                    if (!(skillCld > 0)){
                        skillTwo_BleedingDash(world, user);
                        stack.set(ModDataComponentTypes.SKILL_COOLDOWN, 20 * 10); // 10s
                        skillCasted = true;
                    } else {
                        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Skill on Cooldown").formatted(Formatting.GRAY), true);
                    }
                } else if (charges == 3) {
                    if (!(ultCld > 0)){
                        skillThree_BleedingDash(world, user);
                        stack.set(ModDataComponentTypes.ULTIMATE_COOLDOWN, 20 * 30); // 30s
                        skillCasted = true;
                    } else {
                        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Ultimate on Cooldown").formatted(Formatting.GRAY), true);
                    }
                }
                if (charges > 0 && skillCasted) resetCharges(stack);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    // === Skill One: Hemorrhage ===
    private void skillOne_BleedingDash(World world, PlayerEntity user) {
        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Blood Arts: Hemorrhage").formatted(Formatting.RED), true);

        Vec3d direction = user.getRotationVec(1.0F).normalize();
        Vec3d startPos  = user.getPos();

        assert user instanceof ServerPlayerEntity;
        WeaponAttackManager.startDash(
                (ServerPlayerEntity) user,
                direction,
                startPos,
                4,     // ticksLeft
                1.5,   // speed
                List.of(
                        new WeaponAttackManager.ParticleConfig(
                                new DustParticleEffect(new Vector3f(1.0F, 0.1F, 0.1F), 1.5F),
                                0.7f,
                                0.0f,
                                15
                        )
                ),
                player -> {
                    Vec3d backToStart = startPos.subtract(player.getPos());

                    // Build a swept box that covers the whole path (start BB union end BB)
                    Box pathBox = player.getBoundingBox()
                            .union(player.getBoundingBox().offset(backToStart))
                            .expand(1.5); // thickness so we don't miss slightly off-center mobs

                    for (LivingEntity target : player.getWorld().getEntitiesByClass(
                            LivingEntity.class, pathBox, e -> e.isAlive() && e != player)) {
                        attackWithMultiplier(player, target, 1.0f);
                        spawnParticles(player.getWorld(), target);
                    }
                }
        );

        // Start SFX
        spawnParticles(world, user);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                ModSounds.SANGUINE_DASH, SoundCategory.PLAYERS);
    }

    // === Skill Two: Paralysis ===
    private void skillTwo_BleedingDash(World world, PlayerEntity user) {
        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Blood Arts: Paralysis").formatted(Formatting.RED), true);

        Vec3d direction = user.getRotationVec(1.0F).normalize();
        Vec3d startPos  = user.getPos();

        assert user instanceof ServerPlayerEntity;
        WeaponAttackManager.startDash(
                (ServerPlayerEntity) user,
                direction,
                startPos,
                4,   // ticksLeft
                1.0, // speed
                List.of(
                        new WeaponAttackManager.ParticleConfig(
                                new DustParticleEffect(new Vector3f(1.0F, 0.1F, 0.1F), 1.5F),
                                0.7f,
                                0.0f,
                                15
                        )
                ),
                player -> {
                    Vec3d backToStart = startPos.subtract(player.getPos());
                    Box pathBox = player.getBoundingBox()
                            .union(player.getBoundingBox().offset(backToStart))
                            .expand(3);

                    for (LivingEntity target : player.getWorld().getEntitiesByClass(
                            LivingEntity.class, pathBox, e -> e.isAlive() && e != player)) {
                        freezeEntity(target, 40);
                        attackWithMultiplier(player, target, 1.5f);
                        spawnParticles(player.getWorld(), target);
                    }
                }
        );

        spawnParticles(world, user);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                ModSounds.SANGUINE_DASH, SoundCategory.PLAYERS);
    }

    // === Skill Three: Crimson Fireworks ===
    private void skillThree_BleedingDash(World world, PlayerEntity user) {
        if (user instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("Blood Arts: Crimson Fireworks").formatted(Formatting.DARK_RED), true);

        Vec3d direction = user.getRotationVec(1.0F).normalize();
        Vec3d startPos  = user.getPos();

        assert user instanceof ServerPlayerEntity;
        WeaponAttackManager.startDash(
                (ServerPlayerEntity) user,
                direction,
                startPos,
                4,   // ticksLeft
                1.5, // speed
                List.of(
                        new WeaponAttackManager.ParticleConfig(
                                new DustParticleEffect(new Vector3f(1.0F, 0.1F, 0.1F), 1.5F),
                                0.7f,
                                0.0f,
                                15
                        )
                ),
                player -> {
                    Vec3d backToStart = startPos.subtract(player.getPos());
                    Box pathBox = player.getBoundingBox()
                            .union(player.getBoundingBox().offset(backToStart))
                            .expand(2.5); // wider sweep for ultimate

                    for (LivingEntity target : player.getWorld().getEntitiesByClass(
                            LivingEntity.class, pathBox, e -> e.isAlive() && e != player)) {
                        attackWithMultiplier(player, target, 2.0f);
                        pullEntity(target, player);
                        spawnParticles(player.getWorld(), target);
                    }

                    // Freeze player, then trigger delayed explosion
                    freezeEntity(player, 40);
                    SKILL_THREE_DELAYED_ATTACK.put(player.getUuid(), new DelayedExplosion(player.getUuid(), this, 40));
                }
        );

        spawnParticles(world, user);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                ModSounds.SANGUINE_DASH, SoundCategory.PLAYERS);
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
    public void attackWithMultiplier(PlayerEntity user, LivingEntity target, float multiplier) {
        if (user.getWorld().isClient) return;

        ServerWorld sw = (ServerWorld) user.getWorld();
        ItemStack stack = user.getMainHandStack();

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
        stack.damage(1, user, EquipmentSlot.MAINHAND);
    }

    private void freezeEntity(LivingEntity entity, int durationTicks) {
        // Max slowness so they can't walk
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, 255, false, false, false));
    }

    protected void pullEntity(LivingEntity target, PlayerEntity player) {
        ENTITY_PULL.put(target.getUuid(), new PullData(target.getUuid(), player.getUuid(), 10));
    }

    private void spawnParticles(World world, LivingEntity target) {
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(
                    new DustParticleEffect(new Vector3f(1.0F, 0.1F, 0.1F), 2.8F),
                    target.getX(), target.getBodyY(0.5), target.getZ(),
                    120, 1.2, 1.2, 1.2, 0.1
            );
        }
    }

    // === Data Classes ===
    private static class DelayedExplosion {
        final UUID playerId; final SanguineSwordItem sword; int ticksLeft;
        DelayedExplosion(UUID playerId, SanguineSwordItem sword, int delay) { this.playerId = playerId; this.sword = sword; this.ticksLeft = delay; }
    }
    private static class PullData {
        final UUID targetId; final UUID playerId; int ticksLeft;
        PullData(UUID targetId, UUID playerId, int ticks) { this.targetId = targetId; this.playerId = playerId; this.ticksLeft = ticks; }
    }
}
