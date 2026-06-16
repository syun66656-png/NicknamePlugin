package com.example.nickname.data;

import java.util.Optional;
import java.util.UUID;

public interface DataManager {

    void init();

    /** uuid 기준 데이터 조회 (캐시) */
    Optional<NicknameData> getByUuid(UUID uuid);

    /** 진짜 닉네임으로 조회 (대소문자 무시) */
    Optional<NicknameData> getByRealName(String realName);

    /** 위장 닉네임으로 조회 */
    Optional<NicknameData> getByFakeName(String fakeName);

    /** uuid + 진짜닉을 기록(접속 시) */
    void touch(UUID uuid, String realName);

    /** 위장닉 설정 */
    void setFakeName(UUID uuid, String realName, String fakeName);

    /** 위장닉 해제 */
    void clearFakeName(UUID uuid);

    /** 어떤 위장 닉네임이 이미 사용 중인지 (자기 자신은 제외) */
    boolean isFakeNameTaken(String fakeName, UUID exclude);

    void saveAll();

    void close();
}
