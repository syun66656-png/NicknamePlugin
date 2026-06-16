package com.example.nickname.data;

import com.example.nickname.NicknamePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class YmlDataManager implements DataManager {

    private final NicknamePlugin plugin;
    private final File file;
    private FileConfiguration yaml;

    // UUID 캐시 + 이름 역인덱스 (모두 lowercase 키)
    private final Map<UUID, NicknameData> byUuid     = new ConcurrentHashMap<>();
    private final Map<String, UUID>       byReal     = new ConcurrentHashMap<>();
    private final Map<String, UUID>       byFake     = new ConcurrentHashMap<>();

    // 더티 플래그 - 자주 저장 부담을 줄이기 위한 비동기 저장 스케줄링용
    private volatile boolean dirty = false;

    // 자동저장 타이머 (close() 에서 cancel)
    private BukkitTask autoSaveTask;

    public YmlDataManager(NicknamePlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "data.yml");
    }

    @Override
    public void init() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException ex) {
                plugin.getLogger().severe("data.yml 생성 실패: " + ex.getMessage());
            }
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection sec = yaml.getConfigurationSection("players");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String real = sec.getString(key + ".real");
                    String fake = sec.getString(key + ".fake");
                    if (real == null) continue;

                    NicknameData data = new NicknameData(uuid, real, (fake == null || fake.isEmpty()) ? null : fake);
                    byUuid.put(uuid, data);
                    byReal.put(real.toLowerCase(Locale.ROOT), uuid);
                    if (data.hasFakeName()) {
                        byFake.put(fake.toLowerCase(Locale.ROOT), uuid);
                    }
                } catch (IllegalArgumentException ignored) { /* invalid uuid */ }
            }
        }

        // 30초 주기 비동기 자동 저장 (dirty 일 때만 실제 IO)
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirty) saveAll();
        }, 20L * 30, 20L * 30);

        plugin.getLogger().info("YML 데이터 로드: " + byUuid.size() + "건");
    }

    @Override
    public Optional<NicknameData> getByUuid(UUID uuid) {
        if (uuid == null) return Optional.empty();
        return Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public Optional<NicknameData> getByRealName(String realName) {
        if (realName == null) return Optional.empty();
        UUID uuid = byReal.get(realName.toLowerCase(Locale.ROOT));
        return uuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public Optional<NicknameData> getByFakeName(String fakeName) {
        if (fakeName == null) return Optional.empty();
        UUID uuid = byFake.get(fakeName.toLowerCase(Locale.ROOT));
        return uuid == null ? Optional.empty() : Optional.ofNullable(byUuid.get(uuid));
    }

    @Override
    public synchronized void touch(UUID uuid, String realName) {
        NicknameData data = byUuid.get(uuid);
        if (data == null) {
            data = new NicknameData(uuid, realName, null);
            byUuid.put(uuid, data);
        } else if (!realName.equalsIgnoreCase(data.getRealName())) {
            // 이름 변경(Mojang 닉 변경 이벤트 등)
            byReal.remove(data.getRealName().toLowerCase(Locale.ROOT));
            data.setRealName(realName);
        }
        byReal.put(realName.toLowerCase(Locale.ROOT), uuid);
        dirty = true;
    }

    @Override
    public synchronized void setFakeName(UUID uuid, String realName, String fakeName) {
        if (uuid == null || realName == null || fakeName == null || fakeName.isEmpty()) {
            return;
        }
        NicknameData data = byUuid.get(uuid);
        if (data == null) {
            data = new NicknameData(uuid, realName, fakeName);
            byUuid.put(uuid, data);
            byReal.put(realName.toLowerCase(Locale.ROOT), uuid);
        } else {
            if (data.hasFakeName()) {
                byFake.remove(data.getFakeName().toLowerCase(Locale.ROOT));
            }
            // realName 도 바뀔 수 있음 (Mojang 닉네임 변경 등) — byReal 인덱스도 갱신
            if (!realName.equalsIgnoreCase(data.getRealName())) {
                byReal.remove(data.getRealName().toLowerCase(Locale.ROOT));
                data.setRealName(realName);
                byReal.put(realName.toLowerCase(Locale.ROOT), uuid);
            }
            data.setFakeName(fakeName);
        }
        byFake.put(fakeName.toLowerCase(Locale.ROOT), uuid);
        dirty = true;
    }

    @Override
    public synchronized void clearFakeName(UUID uuid) {
        NicknameData data = byUuid.get(uuid);
        if (data == null || !data.hasFakeName()) return;
        byFake.remove(data.getFakeName().toLowerCase(Locale.ROOT));
        data.setFakeName(null);
        dirty = true;
    }

    @Override
    public boolean isFakeNameTaken(String fakeName, UUID exclude) {
        if (fakeName == null) return false;
        UUID owner = byFake.get(fakeName.toLowerCase(Locale.ROOT));
        if (owner == null) return false;
        return !owner.equals(exclude);
    }

    @Override
    public synchronized void saveAll() {
        if (!dirty && file.exists()) return;
        yaml.set("players", null);
        for (NicknameData data : byUuid.values()) {
            String path = "players." + data.getUuid().toString();
            yaml.set(path + ".real", data.getRealName());
            yaml.set(path + ".fake", data.hasFakeName() ? data.getFakeName() : null);
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("data.yml 저장 실패: " + ex.getMessage());
        }
    }

    @Override
    public void close() {
        if (autoSaveTask != null) {
            try { autoSaveTask.cancel(); } catch (Exception ignored) {}
            autoSaveTask = null;
        }
        saveAll();
    }
}
