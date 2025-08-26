package com.lycoris.modid.item.custom;

import com.lycoris.modid.item.ModItems;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lucidity sword: dashes, invulnerability ultimate, dual-combo handling.
 */
public class LucidityItem extends SwordItem {
    // === Combo / Flow tracking ===
    private static final Map<UUID, ComboState> COMBOS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> FLOW_TIMERS = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID id = player.getUuid();

                // Flow countdown
                FLOW_TIMERS.computeIfPresent(id, (k, v) -> {
                    if (v <= 0) {
                        player.sendMessage(Text.of("§7[Flow ended]"), true);
                        return null;
                    }
                    return v - 1;
                });

                // Combo expiration
                ComboState st = COMBOS.get(id);
                if (st != null && System.currentTimeMillis() > st.deadline) {
                    COMBOS.remove(id);
                    player.sendMessage(Text.of("§c[Combo failed]"), true);
                }
            }
        });
    }

    public LucidityItem(ToolMaterial material, Settings settings) {
        super(material, settings);
    }

    // === Attack handling ===
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayerEntity player)) return super.postHit(stack, target, attacker);

        // Double hit if paired with Delusion
        if (player.getOffHandStack().getItem() instanceof DelusionItem) {
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
                FLOW_TIMERS.put(player.getUuid(), 20 * 10); // 10s Flow
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

    // === Skill / Ultimate ===
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
        Vec3d dir = player.getRotationVector();
        player.addVelocity(dir.x * 1.5, 0.2, dir.z * 1.5);
        player.velocityModified = true;
        player.sendMessage(Text.of("§e[Lucidity Dash]"), true);

        ComboState st = COMBOS.get(player.getUuid());
        if (st != null) {
            st.next = (st.next == Click.LEFT ? Click.RIGHT : Click.LEFT);
        }
    }

    private void activateUltimate(ServerPlayerEntity player, ItemStack stack) {
        FLOW_TIMERS.put(player.getUuid(), 20 * 10);
        COMBOS.put(player.getUuid(), new ComboState(Click.LEFT, 9999, 9999));
        player.sendMessage(Text.of("§d[Lucidity Ultimate: Invulnerable + Free Combo]"), true);

        // TODO: Apply actual invulnerability for 10s
    }

    // === Inner helper classes ===
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

