package com.example.nickname.data;

import java.util.UUID;

public class NicknameData {

    private final UUID uuid;
    private volatile String realName;
    private volatile String fakeName; // nullable

    public NicknameData(UUID uuid, String realName, String fakeName) {
        this.uuid = uuid;
        this.realName = realName;
        this.fakeName = fakeName;
    }

    public UUID getUuid()         { return uuid; }
    public String getRealName()   { return realName; }
    public String getFakeName()   { return fakeName; }

    public boolean hasFakeName()  { return fakeName != null && !fakeName.isEmpty(); }

    public void setRealName(String realName) { this.realName = realName; }
    public void setFakeName(String fakeName) { this.fakeName = fakeName; }

    /** 표시용 - 위장이 있으면 위장, 없으면 진짜 */
    public String getDisplayName() {
        return hasFakeName() ? fakeName : realName;
    }
}
