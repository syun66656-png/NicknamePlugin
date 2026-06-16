package com.example.nickname.api;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.data.NicknameData;
import com.example.nickname.manager.NicknameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/**
 * 다른 플러그인 연동용 공개 API.
 *
 * <p><b>귓속말 플러그인 예시</b> (NicknamePlugin 을 softdepend 로 두고 빌드 classpath 에 JAR 추가)
 * <pre>{@code
 * // 1) 대상 찾기 — 입력이 위장닉이어도 동작
 * Optional<Player> target = NicknameAPI.findOnlinePlayerByAnyName(args[0]);
 *
 * // 2) 메시지에 쓸 이름 (config: use-fake=true → 위장 우선)
 * String senderLabel = NicknameAPI.resolveForMessaging(sender, useFakeInWhisper);
 * }</pre>
 *
 * <p><b>PlaceholderAPI만 사용</b> (코드 수정 없이 messages.yml)
 * <ul>
 *   <li>{@code %nickname_display%} — 위장 있으면 위장, 없으면 진짜</li>
 *   <li>{@code %nickname_real%} — 항상 진짜(Mojang) 닉</li>
 *   <li>{@code %nickname_fake%} — 위장만 (없으면 빈 문자열)</li>
 * </ul>
 * 수신자/발신자 기준은 귓속말 플러그인이 PAPI에 넘기는 {@link org.bukkit.OfflinePlayer} 에 따름.
 */
public final class NicknameAPI {

    private NicknameAPI() {}

    /** NicknamePlugin 이 활성화되어 API를 쓸 수 있는지 */
    public static boolean isAvailable() {
        Plugin p = Bukkit.getPluginManager().getPlugin("NicknamePlugin");
        return p instanceof NicknamePlugin plugin && plugin.isEnabled();
    }

    private static NicknameManager manager() {
        NicknamePlugin plugin = NicknamePlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            throw new IllegalStateException("NicknamePlugin is not enabled");
        }
        return plugin.getNicknameManager();
    }

    /** 위장닉 또는 진짜닉 문자열로 프로필 조회 (오프라인 UUID 포함) */
    public static Optional<NicknameProfile> findByAnyName(String name) {
        if (!isAvailable() || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return manager().findByAny(name.trim())
                .map(NicknameAPI::toProfile);
    }

    public static Optional<NicknameProfile> findByUuid(UUID uuid) {
        if (!isAvailable() || uuid == null) {
            return Optional.empty();
        }
        return manager().findByUuid(uuid).map(NicknameAPI::toProfile);
    }

    /** 온라인 플레이어만 — 귓속말 대상 탐색용 */
    public static Optional<Player> findOnlinePlayerByAnyName(String name) {
        return findByAnyName(name).flatMap(NicknameProfile::onlinePlayer);
    }

    public static String resolveDisplay(Player player) {
        return manager().resolveDisplay(player);
    }

    public static String resolveDisplay(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return manager().resolveDisplay(online);
        }
        return findByUuid(uuid).map(NicknameProfile::displayName).orElse("");
    }

    public static String resolveReal(Player player) {
        return manager().resolveReal(player);
    }

    public static String resolveFake(Player player) {
        return manager().resolveFake(player);
    }

    public static boolean hasFakeNickname(Player player) {
        return manager().hasFake(player);
    }

    /**
     * 귓속말·쪽지 등에 표시할 이름.
     *
     * @param useFake true → {@link #resolveDisplay(Player)} (위장 우선),
     *                false → {@link #resolveReal(Player)} (항상 진짜 닉)
     */
    public static String resolveForMessaging(Player player, boolean useFake) {
        return useFake ? resolveDisplay(player) : resolveReal(player);
    }

    private static NicknameProfile toProfile(NicknameData data) {
        return new NicknameProfile(
                data.getUuid(),
                data.getRealName(),
                data.hasFakeName() ? data.getFakeName() : null
        );
    }
}
