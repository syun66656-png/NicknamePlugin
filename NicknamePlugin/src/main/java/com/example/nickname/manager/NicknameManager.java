package com.example.nickname.manager;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.api.event.PlayerNicknameChangeEvent;
import com.example.nickname.config.ConfigManager;
import com.example.nickname.data.NicknameData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class NicknameManager {

    private final NicknamePlugin plugin;

    public NicknameManager(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    /* ============================================================
     *  조회
     * ============================================================ */

    public Optional<NicknameData> findByAny(String name) {
        if (name == null) return Optional.empty();
        // 1) 위장닉이 먼저 (위장닉이 누군가의 진짜닉과 같을 가능성을 막기 위해 우선 위장 조회)
        Optional<NicknameData> opt = plugin.getDataManager().getByFakeName(name);
        if (opt.isPresent()) return opt;

        // 2) 진짜닉 (캐시)
        opt = plugin.getDataManager().getByRealName(name);
        if (opt.isPresent()) return opt;

        // 3) 캐시에 없으면 OfflinePlayer 조회 (서버에 들어온 적 없으면 hasPlayedBefore=false)
        @SuppressWarnings("deprecation")
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.hasPlayedBefore() || op.isOnline()) {
            plugin.getDataManager().touch(op.getUniqueId(), op.getName() == null ? name : op.getName());
            return plugin.getDataManager().getByUuid(op.getUniqueId());
        }
        return Optional.empty();
    }

    public Optional<NicknameData> findByUuid(UUID uuid) {
        return plugin.getDataManager().getByUuid(uuid);
    }

    public boolean hasFakeName(UUID uuid) {
        return findByUuid(uuid).map(NicknameData::hasFakeName).orElse(false);
    }

    /* ============================================================
     *  변경 / 초기화
     * ============================================================ */

    /** 검증 결과 코드 */
    public enum SetResult {
        OK,
        INVALID_FORMAT,
        SAME_AS_CURRENT,
        TAKEN,
        IS_OTHER_REAL_NAME
    }

    /** 부작용 없이 위장 닉네임이 사용 가능한지 검증만 한다. */
    public SetResult validate(Player player, String fakeName) {
        ConfigManager cfg = plugin.getConfigManager();
        String normalized = fakeName == null ? "" : fakeName.trim();

        if (!com.example.nickname.util.NicknameValidator.isValidFormat(normalized, cfg)) {
            return SetResult.INVALID_FORMAT;
        }

        if (hasFakeName(player.getUniqueId())) {
            String current = findByUuid(player.getUniqueId())
                    .map(NicknameData::getFakeName)
                    .orElse("");
            if (!current.isEmpty() && current.equalsIgnoreCase(normalized)) {
                return SetResult.SAME_AS_CURRENT;
            }
        }

        // 자기 자신의 진짜 닉네임은 위장으로 못 쓰게 차단
        if (player.getName().equalsIgnoreCase(normalized)) {
            return SetResult.INVALID_FORMAT;
        }

        // 이미 같은 위장닉을 쓰는 사람이 있는지
        if (cfg.isUnique() && plugin.getDataManager().isFakeNameTaken(normalized, player.getUniqueId())) {
            return SetResult.TAKEN;
        }

        // 다른 플레이어의 진짜 닉네임을 위장으로 못 쓰게 차단
        if (cfg.isBlockExistingRealName()) {
            // 1) 캐시(메모리) 우선 조회 — 블로킹 없음
            Optional<NicknameData> realOwner = plugin.getDataManager().getByRealName(normalized);
            if (realOwner.isPresent() && !realOwner.get().getUuid().equals(player.getUniqueId())) {
                return SetResult.IS_OTHER_REAL_NAME;
            }
            // 2) 캐시에 없을 때만 OfflinePlayer 폴백.
            //    주의: 이름 기반 getOfflinePlayer 는 usercache.json 미스 시 메인 스레드를 잠깐
            //    블로킹할 수 있다. validate 는 GUI 클릭에서 동기 호출되므로 캐시 히트 시 이 경로를
            //    타지 않도록 위에서 먼저 캐시를 확인한다.
            if (realOwner.isEmpty()) {
                @SuppressWarnings("deprecation")
                OfflinePlayer op = Bukkit.getOfflinePlayer(normalized);
                if (op.hasPlayedBefore() && !op.getUniqueId().equals(player.getUniqueId())) {
                    return SetResult.IS_OTHER_REAL_NAME;
                }
            }
        }

        return SetResult.OK;
    }

    /** 위장 닉네임을 실제로 적용한다. (검증은 validate 로 먼저 수행할 것) */
    public void applyFakeName(Player player, String fakeName) {
        if (player == null || fakeName == null || fakeName.isEmpty()) return;
        String oldFake = findByUuid(player.getUniqueId())
                .filter(NicknameData::hasFakeName)
                .map(NicknameData::getFakeName)
                .orElse(null);
        plugin.getDataManager().setFakeName(player.getUniqueId(), player.getName(), fakeName);
        refreshDisplay(player);
        pushPacketRefresh(player);
        fireChange(player, PlayerNicknameChangeEvent.ChangeType.SET, oldFake, fakeName);
    }

    public void resetNickname(UUID uuid) {
        if (uuid == null) return;
        String oldFake = findByUuid(uuid)
                .filter(NicknameData::hasFakeName)
                .map(NicknameData::getFakeName)
                .orElse(null);
        plugin.getDataManager().clearFakeName(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            refreshDisplay(p);
            pushPacketRefresh(p);
            fireChange(p, PlayerNicknameChangeEvent.ChangeType.RESET, oldFake, null);
        }
    }

    /**
     * 위장닉이 바뀐 직후, 모든 viewer 의 클라이언트에서 대상을 hide/show 사이클로 재로딩 시켜
     * 새 PLAYER_INFO_UPDATE 가 우리 packet listener 를 거치도록 한다.
     * PacketEvents 가 없으면 자동 스킵.
     */
    public void pushPacketRefresh(Player player) {
        var listener = plugin.getNameTagPacketListener();
        if (listener != null && player != null && player.isOnline()) {
            listener.refreshForAllViewers(player);
        }
    }

    private void fireChange(
            Player player,
            PlayerNicknameChangeEvent.ChangeType type,
            String oldFake,
            String newFake
    ) {
        // 실제 변경이 없으면 이벤트 발화 X
        if (java.util.Objects.equals(oldFake, newFake)) {
            return;
        }
        Bukkit.getPluginManager().callEvent(
                new PlayerNicknameChangeEvent(player, type, player.getName(), oldFake, newFake));
    }

    /* ============================================================
     *  표시명 (탭리스트만)
     * ============================================================ */

    public void refreshDisplay(Player player) {
        if (player == null) return;
        ConfigManager cfg = plugin.getConfigManager();

        // 탭리스트 표시명
        if (cfg.isTabListEnabled()) {
            String display = resolveDisplay(player);
            Component comp = Component.text(display);
            player.playerListName(comp);
        }

        // 머리 위 닉네임은 NameTagPacketListener 가 PLAYER_INFO_UPDATE 패킷을 가로채서 처리.
        // viewer 들의 클라이언트에 변경된 위장닉을 푸시하는 것은 applyFakeName/resetNickname 및
        // pollRemoteChanges 에서 명시적으로 pushPacketRefresh() 를 호출한다.
        //
        // 채팅 발신자 이름은 일부러 손대지 않습니다 (Bukkit 의 player.displayName / 
        // %player_displayname% 미사용). 채팅 포맷은 채팅 플러그인이 본 플러그인의
        // %nickname_display% 등 PAPI 자리표시자를 직접 사용하도록 두세요.
    }

    /** 표시용 이름 - 위장 우선 (display-mode = real 인 경우 항상 진짜) */
    public String resolveDisplay(Player player) {
        if (player == null) return "";
        if (plugin.getConfigManager().getDisplayMode() == ConfigManager.DisplayMode.REAL) {
            return player.getName();
        }
        return findByUuid(player.getUniqueId())
                .map(NicknameData::getDisplayName)
                .orElse(player.getName());
    }

    public String resolveReal(Player player) {
        return player == null ? "" : player.getName();
    }

    public String resolveFake(Player player) {
        if (player == null) return "";
        return findByUuid(player.getUniqueId()).map(d -> d.hasFakeName() ? d.getFakeName() : "").orElse("");
    }

    public boolean hasFake(Player player) {
        return player != null && hasFakeName(player.getUniqueId());
    }
}
