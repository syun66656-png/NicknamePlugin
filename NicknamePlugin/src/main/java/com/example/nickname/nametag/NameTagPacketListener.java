package com.example.nickname.nametag;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.data.NicknameData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PacketEvents 리스너 — {@code PLAYER_INFO_UPDATE} 패킷을 가로채서
 * <b>플레이어 머리 위 닉네임</b>을 위장닉으로 바꿔치기한다.
 *
 * <h3>구현</h3>
 * <p>머리 위 닉네임은 클라이언트가 {@code GameProfile.name} 을 그대로 렌더링하므로,
 * ADD_PLAYER 액션이 담긴 패킷에서 위장닉이 있는 플레이어의 GameProfile name 을 위장닉으로 교체한다.
 * 위장닉이 없는 플레이어는 손대지 않는다 → 진짜 닉이 그대로 보임.
 *
 * <p>탭리스트는 기존 {@code tab-list.enabled} 설정 그대로 동작하도록, {@code false} 인 경우
 * 같은 엔트리의 {@code displayName} 필드를 <b>진짜 이름</b> 으로 덮어쓰고 UPDATE_DISPLAY_NAME
 * 액션을 추가한다 (클라이언트가 GameProfile name = 위장닉으로 fallback 하지 않도록 보호).
 *
 * <h3>외부 플러그인 주의사항</h3>
 * <p>Velocitab 의 {@code remove_nametags: true} 또는 다른 nametag 가림 기능과 같이 사용하면
 * 우리 패킷보다 뒤에 적용되는 스코어보드 팀 규칙(visibility=NEVER)이 모든 nametag 를 가려버려서
 * 결과적으로 머리 위에 아무것도 표시되지 않는다. 이 플러그인을 머리 위 닉네임의 주체로 쓰려면
 * Velocitab 의 {@code remove_nametags} 를 <b>false</b> 로 두어야 한다.
 *
 * <h3>영향 범위</h3>
 * <ul>
 *   <li>머리 위 닉네임: 위장닉(있을 때) / 진짜 닉(없을 때)</li>
 *   <li>탭리스트: 기존 {@code tab-list.enabled} 설정 그대로</li>
 *   <li>채팅 / 명령어 / 권한 / 저장소: 영향 없음 (서버 측 {@code player.getName()} 그대로)</li>
 * </ul>
 */
public class NameTagPacketListener extends PacketListenerAbstract {

    private final NicknamePlugin plugin;

    /** 패킷 가공 실패 경고 레이트리밋: 같은 메시지를 이 간격(ms) 안에는 한 번만 출력. */
    private static final long WARN_THROTTLE_MILLIS = 60_000L;
    private volatile long lastWarnAt = 0L;
    private volatile String lastWarnMsg = null;

    public NameTagPacketListener(NicknamePlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!plugin.getConfigManager().isNameTagEnabled()) return;
        if (event.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE) return;
        if (plugin.getDataManager() == null) return;

        try {
            WrapperPlayServerPlayerInfoUpdate wrapper = new WrapperPlayServerPlayerInfoUpdate(event);

            EnumSet<Action> actions = wrapper.getActions();
            if (!actions.contains(Action.ADD_PLAYER)) return;

            boolean tabListEnabled = plugin.getConfigManager().isTabListEnabled();
            List<PlayerInfo> entries = wrapper.getEntries();

            boolean modifiedAny = false;
            boolean setDisplayNameAny = false;

            for (PlayerInfo entry : entries) {
                UserProfile profile = entry.getGameProfile();
                if (profile == null) continue;

                UUID uuid = profile.getUUID();
                if (uuid == null) continue;

                Optional<NicknameData> dataOpt = plugin.getDataManager().getByUuid(uuid);
                if (dataOpt.isEmpty()) continue;
                NicknameData data = dataOpt.get();
                if (!data.hasFakeName()) continue;

                String fakeName = data.getFakeName();
                if (fakeName == null || fakeName.isEmpty()) continue;

                // 머리 위 닉네임 = 위장닉 (GameProfile name 교체, skin texture 는 보존)
                if (!fakeName.equals(profile.getName())) {
                    UserProfile rewritten = new UserProfile(uuid, fakeName, profile.getTextureProperties());
                    entry.setGameProfile(rewritten);
                    modifiedAny = true;
                }

                // 탭리스트가 꺼져 있으면 (= 사용자가 vanilla 진짜 이름 표시 원함)
                // displayName 을 진짜 이름으로 강제 덮어써서 위장닉이 탭리스트로 새지 않게 보호.
                if (!tabListEnabled) {
                    entry.setDisplayName(Component.text(data.getRealName()));
                    setDisplayNameAny = true;
                }
            }

            if (modifiedAny || setDisplayNameAny) {
                wrapper.setEntries(entries);
                if (setDisplayNameAny && !actions.contains(Action.UPDATE_DISPLAY_NAME)) {
                    EnumSet<Action> newActions = EnumSet.copyOf(actions);
                    newActions.add(Action.UPDATE_DISPLAY_NAME);
                    wrapper.setActions(newActions);
                }
            }
        } catch (Throwable t) {
            warnThrottled("PLAYER_INFO_UPDATE 가공 실패: " + t.getMessage());
        }
    }

    /**
     * 고빈도 패킷 경로에서 같은 경고가 콘솔을 도배하지 않도록 레이트리밋.
     * 같은 메시지는 {@link #WARN_THROTTLE_MILLIS} 간격으로 한 번만 출력한다.
     */
    private void warnThrottled(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastWarnMsg) && (now - lastWarnAt) < WARN_THROTTLE_MILLIS) {
            return;
        }
        lastWarnAt = now;
        lastWarnMsg = message;
        plugin.getLogger().warning(message);
    }

    /**
     * 위장닉 변경/초기화 직후 호출. 모든 viewer 의 클라이언트에서 대상 플레이어를 hide/show
     * 하여 Bukkit 이 새 PLAYER_INFO_UPDATE(ADD_PLAYER) 를 재전송하도록 한다. 그 패킷이
     * 이 리스너를 다시 거치면서 새 위장닉이 머리 위에 적용된다.
     *
     * <p>본인 자신은 제외.
     */
    public void refreshForAllViewers(Player target) {
        if (target == null || !target.isOnline()) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) continue;
            try {
                viewer.hidePlayer(plugin, target);
                viewer.showPlayer(plugin, target);
            } catch (Exception ignored) {
                // viewer 가 disconnect 중일 수 있음
            }
        }
    }

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void unregister() {
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(this);
        } catch (Throwable ignored) {
            // PacketEvents 가 이미 비활성 상태일 수 있음
        }
    }
}
