package com.example.nickname.config;

import com.example.nickname.NicknamePlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * config.yml 의 sounds.* 섹션을 재생한다.
 */
public class SoundPlayer {

    private final NicknamePlugin plugin;

    public SoundPlayer(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, String key) {
        if (player == null || key == null || key.isEmpty()) {
            return;
        }
        FileConfiguration cfg = plugin.getConfig();
        String path = "sounds." + key;
        String soundName = cfg.getString(path + ".sound", "none");
        if (soundName == null || soundName.isBlank() || "none".equalsIgnoreCase(soundName)) {
            return;
        }
        float volume = (float) cfg.getDouble(path + ".volume", 1.0);
        float pitch = (float) cfg.getDouble(path + ".pitch", 1.0);
        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("알 수 없는 사운드: " + soundName + " (sounds." + key + ")");
        }
    }
}
