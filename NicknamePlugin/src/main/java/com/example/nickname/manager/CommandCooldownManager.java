package com.example.nickname.manager;

import com.example.nickname.NicknamePlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /닉네임 명령어 쿨타임 (OP 는 기본적으로 제외) */
public class CommandCooldownManager {

    private final NicknamePlugin plugin;
    private final Map<UUID, Long> lastUseMs = new ConcurrentHashMap<>();

    public CommandCooldownManager(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean bypass(Player player) {
        if (player == null) {
            return true;
        }
        return plugin.getConfig().getBoolean("command-cooldown.bypass-op", true) && player.isOp();
    }

    public long getCooldownMillis() {
        double seconds = plugin.getConfig().getDouble("command-cooldown.seconds", 3.0);
        return Math.max(0L, (long) (seconds * 1000.0));
    }

    public boolean isOnCooldown(Player player) {
        if (player == null || bypass(player)) {
            return false;
        }
        long cooldown = getCooldownMillis();
        if (cooldown <= 0L) {
            return false;
        }
        Long last = lastUseMs.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldown;
    }

    public long getRemainingSeconds(Player player) {
        if (player == null || bypass(player)) {
            return 0L;
        }
        long cooldown = getCooldownMillis();
        Long last = lastUseMs.get(player.getUniqueId());
        if (last == null) {
            return 0L;
        }
        long remain = cooldown - (System.currentTimeMillis() - last);
        return remain <= 0L ? 0L : (remain + 999L) / 1000L;
    }

    /** 쿨타임이 아니면 사용 시각을 기록하고 true */
    public boolean tryUse(Player player) {
        if (player == null || bypass(player)) {
            return true;
        }
        if (isOnCooldown(player)) {
            return false;
        }
        lastUseMs.put(player.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    public void clear(Player player) {
        if (player != null) {
            lastUseMs.remove(player.getUniqueId());
        }
    }
}
