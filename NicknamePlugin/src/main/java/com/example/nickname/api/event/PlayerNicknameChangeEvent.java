package com.example.nickname.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 위장 닉네임이 설정되거나 초기화될 때 발생.
 * 귓속말 플러그인 등에서 캐시 갱신용으로 사용.
 */
public class PlayerNicknameChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum ChangeType {
        /** 위장 닉네임 적용 */
        SET,
        /** 위장 닉네임 제거 */
        RESET
    }

    private final ChangeType changeType;
    private final String realName;
    private final @Nullable String oldFakeName;
    private final @Nullable String newFakeName;

    public PlayerNicknameChangeEvent(
            @NotNull Player player,
            @NotNull ChangeType changeType,
            @NotNull String realName,
            @Nullable String oldFakeName,
            @Nullable String newFakeName
    ) {
        super(player);
        this.changeType = changeType;
        this.realName = realName;
        this.oldFakeName = emptyToNull(oldFakeName);
        this.newFakeName = emptyToNull(newFakeName);
    }

    public @NotNull ChangeType getChangeType() {
        return changeType;
    }

    public @NotNull String getRealName() {
        return realName;
    }

    public @Nullable String getOldFakeName() {
        return oldFakeName;
    }

    public @Nullable String getNewFakeName() {
        return newFakeName;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static @Nullable String emptyToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
