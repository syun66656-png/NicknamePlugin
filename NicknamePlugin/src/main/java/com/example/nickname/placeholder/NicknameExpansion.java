package com.example.nickname.placeholder;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.data.NicknameData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * PlaceholderAPI 확장.
 *
 *  %nickname_display%     → 위장닉이 있으면 위장, 없으면 진짜 (탭/채팅 등에 사용)
 *  %nickname_real%        → 진짜 닉네임
 *  %nickname_fake%        → 위장 닉네임 (없으면 빈 문자열)
 *  %nickname_has_fake%    → 위장닉 보유 여부 (true / false)
 *  %nickname_status%      → 위장닉 보유 시 위장닉 문자열, 아니면 "없음" (자유 사용)
 */
public class NicknameExpansion extends PlaceholderExpansion {

    private final NicknamePlugin plugin;

    public NicknameExpansion(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "nickname"; }
    @Override public @NotNull String getAuthor()     { return "NicknamePlugin"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }
    @Override public boolean canRegister()           { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // 온라인 플레이어면 가장 정확함
        if (player instanceof Player online) {
            return forOnline(online, params);
        }

        // 오프라인 - 캐시 기반
        Optional<NicknameData> opt = plugin.getDataManager().getByUuid(player.getUniqueId());
        String real = opt.map(NicknameData::getRealName).orElse(player.getName() == null ? "" : player.getName());
        String fake = opt.map(d -> d.hasFakeName() ? d.getFakeName() : "").orElse("");
        boolean hasFake = !fake.isEmpty();
        String display = hasFake ? fake : real;

        return switch (params.toLowerCase()) {
            case "display"  -> display;
            case "real"     -> real;
            case "fake"     -> fake;
            case "has_fake" -> Boolean.toString(hasFake);
            case "status"   -> hasFake ? fake : "없음";
            default         -> null;
        };
    }

    private String forOnline(Player p, String params) {
        String real    = plugin.getNicknameManager().resolveReal(p);
        String fake    = plugin.getNicknameManager().resolveFake(p);
        String display = plugin.getNicknameManager().resolveDisplay(p);
        boolean hasFake = plugin.getNicknameManager().hasFake(p);

        return switch (params.toLowerCase()) {
            case "display"  -> display;
            case "real"     -> real;
            case "fake"     -> fake;
            case "has_fake" -> Boolean.toString(hasFake);
            case "status"   -> hasFake ? fake : "없음";
            default         -> null;
        };
    }
}
