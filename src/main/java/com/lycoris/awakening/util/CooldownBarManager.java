package com.lycoris.awakening.util;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownBarManager {

    // Keyed by (playerUUID + abilityName)
    private static final Map<String, ServerBossBar> ACTIVE_BARS = new HashMap<>();

    private static String key(UUID playerId, String abilityName) {
        return playerId.toString() + "::" + abilityName;
    }

    private static void updateBar(ServerPlayerEntity player,
                                  String abilityName,
                                  BossBar.Color color,
                                  int current,
                                  int max) {
        String key = key(player.getUuid(), abilityName);

        if (current > 0) {
            ServerBossBar bar = ACTIVE_BARS.computeIfAbsent(key,
                    k -> new ServerBossBar(Text.literal(abilityName), color, BossBar.Style.NOTCHED_10)
            );
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
            float progress = Math.max(0.0F, Math.min(1.0F, current / (float) max));
            bar.setPercent(progress);
        } else {
            removeBar(player, abilityName);
        }
    }

    public static void updateSkillBar(ServerPlayerEntity player, String abilityName, int cooldown, int maxCooldown) {
        updateBar(player, abilityName, BossBar.Color.RED, cooldown, maxCooldown);
    }

    public static void updateUltimateBar(ServerPlayerEntity player, String abilityName, int cooldown, int maxCooldown) {
        updateBar(player, abilityName, BossBar.Color.PURPLE, cooldown, maxCooldown);
    }

    public static void updateDurationBar(ServerPlayerEntity player, String abilityName, int current, int max) {
        updateBar(player, abilityName, BossBar.Color.YELLOW, current, max);
    }

    public static void removeBar(ServerPlayerEntity player, String abilityName) {
        ServerBossBar bar = ACTIVE_BARS.remove(key(player.getUuid(), abilityName));
        if (bar != null) {
            bar.removePlayer(player);
        }
    }
}
