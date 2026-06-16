package com.example.nickname.util;

import com.example.nickname.config.ConfigManager;

public class NicknameValidator {

    private NicknameValidator() {}

    public static boolean isValidFormat(String name, ConfigManager cfg) {
        if (name == null) return false;
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return false;
        // codePoint 기준 글자수 (한글은 1코드포인트=1글자라 length() 와 동일하지만 안전하게)
        int len = trimmed.codePointCount(0, trimmed.length());
        if (len < cfg.getMinLength() || len > cfg.getMaxLength()) return false;
        return cfg.getNicknamePattern().matcher(trimmed).matches();
    }
}
