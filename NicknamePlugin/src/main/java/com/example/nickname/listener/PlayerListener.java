package com.example.nickname.listener;

import com.example.nickname.NicknamePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 플레이어 라이프사이클 이벤트.
 *
 * <p>본 플러그인은 머리 위 닉네임이나 채팅 포맷을 직접 다루지 않습니다.
 * 머리 위 닉네임 표시는 Velocitab + PAPI({@code %nickname_display%}) 위임,
 * 채팅 포맷은 다른 채팅 플러그인(EssentialsChat, DeluxeChat 등) + PAPI 위임.
 * 따라서 join 이벤트에서 캐시/표시명만 갱신하면 됩니다.
 */
public class PlayerListener implements Listener {

    private final NicknamePlugin plugin;

    public PlayerListener(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getDataManager().touch(p.getUniqueId(), p.getName());
        plugin.getNicknameManager().refreshDisplay(p);
    }

    /**
     * 퇴장 시 쿨타임 맵에서 UUID 제거.
     * 제거하지 않으면 서버가 오래 실행될수록 맵이 무한히 커지는 메모리 누수가 발생한다.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCommandCooldownManager().clear(event.getPlayer());
    }
}
