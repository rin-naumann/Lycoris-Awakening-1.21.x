package com.lycoris.modid.util;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownBarManager {

    private static final Map<UUID, ServerBossBar> SKILL_BARS = new HashMap<>();
    private static final Map<UUID, ServerBossBar> ULTIMATE_BARS = new HashMap<>();

    // === Skill Cooldown ===
    public static void updateSkillBar(ServerPlayerEntity player, int cooldown, int maxCooldown) {
        UUID id = player.getUuid();

        if (cooldown > 0) {
            ServerBossBar bar = SKILL_BARS.computeIfAbsent(id, uuid ->
                    new ServerBossBar(Text.literal("Skill Cooldown"), BossBar.Color.RED, BossBar.Style.NOTCHED_10)
            );
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
            float progress = Math.max(0.0F, Math.min(1.0F, cooldown / (float) maxCooldown));
            bar.setPercent(progress);
        } else {
            removeSkillBar(player);
        }
    }

    public static void removeSkillBar(ServerPlayerEntity player) {
        ServerBossBar bar = SKILL_BARS.remove(player.getUuid());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    // === Ultimate Cooldown ===
    public static void updateUltimateBar(ServerPlayerEntity player, int cooldown, int maxCooldown) {
        UUID id = player.getUuid();

        if (cooldown > 0) {
            ServerBossBar bar = ULTIMATE_BARS.computeIfAbsent(id, uuid ->
                    new ServerBossBar(Text.literal("Ultimate Cooldown"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10)
            );
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
            float progress = Math.max(0.0F, Math.min(1.0F, cooldown / (float) maxCooldown));
            bar.setPercent(progress);
        } else {
            removeUltimateBar(player);
        }
    }

    public static void removeUltimateBar(ServerPlayerEntity player) {
        ServerBossBar bar = ULTIMATE_BARS.remove(player.getUuid());
        if (bar != null) {
            bar.removePlayer(player);
        }
    }

    // Cleanup when player leaves
    public static void clearAll(ServerPlayerEntity player) {
        removeSkillBar(player);
        removeUltimateBar(player);
    }
}

