package com.lycoris.modid.item.custom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delusion sword: projectile throw, AOE ultimate, combo handling.
 */
public class DelusionItem extends SwordItem {
    private static final Map<UUID, ComboState> COMBOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> FLOW_TIMERS = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();

                FLOW_TIMERS.computeIfPresent(id, (k, v) -> {
                    if (v <= 0) {
                        player.sendMessage(Text.of("§7[Flow ended]"), true);
                        return null;
                    }
                    return v - 1;
                });

                ComboState st = COMBOS.get(id);
                if (st != null && System.currentTimeMillis() > st.deadline) {
                    COMBOS.remove(id);
                    player.sendMessage(Text.of("§c[Combo failed]"), true);
                }
            }
        });
    }

    public DelusionItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayerEntity player)) return super.postHit(stack, target, attacker);

        if (player.getOffHandStack().getItem() instanceof LucidityItem) {
            target.damage(player.getDamageSources().playerAttack(player), 4.0F);
        }

        handleCombo(player, Hand.MAIN_HAND);
        return super.postHit(stack, target, attacker);
    }

    private void handleCombo(ServerPlayerEntity player, Hand hand) {
        ComboState state = COMBOS.computeIfAbsent(player.getUuid(), k -> new ComboState());
        if (System.currentTimeMillis() > state.deadline) {
            resetCombo(player);
            return;
        }

        Click input = (hand == Hand.MAIN_HAND ? Click.LEFT : Click.RIGHT);
        if (state.next == input) {
            state.streak++;
            state.next = RNG.nextBoolean() ? Click.LEFT : Click.RIGHT;
            state.deadline = System.currentTimeMillis() + 2000;

            player.sendMessage(Text.of("§a[Combo " + state.streak + "] Next: " + state.next), true);

            if (state.streak >= 3) {
                FLOW_TIMERS.put(player.getUuid(), 20 * 10);
                player.sendMessage(Text.of("§b[Flow activated!]"), true);
            }
        } else {
            resetCombo(player);
        }
    }

    private void resetCombo(ServerPlayerEntity player) {
        COMBOS.remove(player.getUuid());
        player.sendMessage(Text.of("§7[Combo reset]"), true);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!(user instanceof ServerPlayerEntity player)) return super.use(world, user, hand);
        ItemStack stack = user.getStackInHand(hand);

        if (player.isSneaking()) {
            if (!world.isClient) activateUltimate(player, stack);
        } else {
            if (!world.isClient) activateSkill(player, stack);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    private void activateSkill(ServerPlayerEntity player, ItemStack stack) {
        // TODO: Replace with custom thrown sword entity
        player.sendMessage(Text.of("§e[Delusion Throw]"), true);
        resetCombo(player); // ends combo as per design
    }

    private void activateUltimate(ServerPlayerEntity player, ItemStack stack) {
        player.sendMessage(Text.of("§d[Delusion Ultimate: AOE storm + deflect]"), true);

        // Simple AOE tick
        Box aoe = player.getBoundingBox().expand(5.0);
        List<LivingEntity> enemies = player.getWorld().getEntitiesByClass(LivingEntity.class, aoe, e -> e != player);
        for (LivingEntity e : enemies) {
            e.damage(player.getDamageSources().playerAttack(player), 6.0F);
        }

        // TODO: repeat for 10s and cancel projectiles hitting player
    }

    private enum Click { LEFT, RIGHT }
    private static class ComboState {
        Click next;
        int streak;
        long deadline;
        ComboState() {
            this.next = RNG.nextBoolean() ? Click.LEFT : Click.RIGHT;
            this.streak = 0;
            this.deadline = System.currentTimeMillis() + 2000;
        }
        ComboState(Click next, long deadline, int streak) {
            this.next = next;
            this.streak = streak;
            this.deadline = System.currentTimeMillis() + deadline;
        }
    }
}
