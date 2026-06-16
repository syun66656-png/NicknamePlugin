package com.example.nickname.config;

import com.example.nickname.NicknamePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.regex.Pattern;

/**
 * config.yml 의 주요 설정 값을 캐시한다.
 * {@link NicknamePlugin#doReload()} 호출 시 {@link #reload()} 로 다시 읽는다.
 */
public class ConfigManager {

    private final NicknamePlugin plugin;

    private Pattern nicknamePattern;
    private int minLength;
    private int maxLength;
    private boolean unique;
    private boolean blockExistingRealName;

    private boolean nameTagEnabled;
    private boolean tabListEnabled;
    private DisplayMode displayMode;

    public enum DisplayMode { DISPLAY, REAL }

    public ConfigManager(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration cfg = plugin.getConfig();

        this.minLength = cfg.getInt("nickname.min-length", 2);
        this.maxLength = cfg.getInt("nickname.max-length", 5);

        String regex = cfg.getString("nickname.regex", "^[가-힣]+$");
        try {
            this.nicknamePattern = Pattern.compile(regex);
        } catch (Exception ex) {
            plugin.getLogger().warning("nickname.regex 가 잘못되어 기본값으로 대체합니다: " + ex.getMessage());
            this.nicknamePattern = Pattern.compile("^[가-힣]+$");
        }

        this.unique = cfg.getBoolean("nickname.unique", true);
        this.blockExistingRealName = cfg.getBoolean("nickname.block-existing-real-name", true);

        this.nameTagEnabled = cfg.getBoolean("name-tag.enabled", true);
        this.tabListEnabled = cfg.getBoolean("tab-list.enabled", false);

        String mode = cfg.getString("display-mode", "display");
        this.displayMode = "real".equalsIgnoreCase(mode) ? DisplayMode.REAL : DisplayMode.DISPLAY;
    }

    public FileConfiguration raw()                  { return plugin.getConfig(); }
    public Pattern getNicknamePattern()             { return nicknamePattern; }
    public int getMinLength()                       { return minLength; }
    public int getMaxLength()                       { return maxLength; }
    public boolean isUnique()                       { return unique; }
    public boolean isBlockExistingRealName()        { return blockExistingRealName; }
    public boolean isNameTagEnabled()               { return nameTagEnabled; }
    public boolean isTabListEnabled()               { return tabListEnabled; }
    public DisplayMode getDisplayMode()             { return displayMode; }
}
