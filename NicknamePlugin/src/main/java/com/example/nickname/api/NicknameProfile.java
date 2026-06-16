package com.example.nickname.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * 닉네임 조회 결과. 다른 플러그인(귓속말, 우편 등) 연동용.
 */
public record NicknameProfile(UUID uuid, String realName, String fakeName) {

    public boolean hasFake() {
        return fakeName != null && !fakeName.isEmpty();
    }

    /** 위장이 있으면 위장, 없으면 진짜 (탭/채팅 표시와 동일) */
    public String displayName() {
        return hasFake() ? fakeName : realName;
    }

    public Optional<Player> onlinePlayer() {
        return Optional.ofNullable(Bukkit.getPlayer(uuid));
    }
}
