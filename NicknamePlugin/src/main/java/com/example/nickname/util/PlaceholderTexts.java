package com.example.nickname.util;

import com.example.nickname.NicknamePlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * messages.yml 의 {@code %placeholder%} (PlaceholderAPI) 치환.
 */
public final class PlaceholderTexts {

    private PlaceholderTexts() {}

    public static String apply(NicknamePlugin plugin, CommandSender audience, String text) {
        if (text == null || text.isEmpty() || !plugin.isPlaceholderApiHooked()) {
            return text;
        }
        if (audience instanceof Player player) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        if (audience instanceof OfflinePlayer offline) {
            return PlaceholderAPI.setPlaceholders(offline, text);
        }
        return text;
    }
}
