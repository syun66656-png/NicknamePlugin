package com.example.nickname;

import com.example.nickname.command.NicknameCommand;
import com.example.nickname.command.NicknameTabCompleter;
import com.example.nickname.config.ConfigManager;
import com.example.nickname.config.MessageManager;
import com.example.nickname.config.SoundPlayer;
import com.example.nickname.data.DataManager;
import com.example.nickname.data.MariaDBDataManager;
import com.example.nickname.data.YmlDataManager;
import com.example.nickname.gui.NicknameAnvilGUI;
import com.example.nickname.listener.PlayerListener;
import com.example.nickname.manager.CommandCooldownManager;
import com.example.nickname.manager.NicknameManager;
import com.example.nickname.manager.TicketManager;
import com.example.nickname.nametag.NameTagPacketListener;
import com.example.nickname.placeholder.NicknameExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class NicknamePlugin extends JavaPlugin {

    private static NicknamePlugin instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DataManager dataManager;
    private NicknameManager nicknameManager;
    private TicketManager ticketManager;
    private NicknameAnvilGUI nicknameAnvilGUI;
    private NameTagPacketListener nameTagPacketListener;
    private CommandCooldownManager commandCooldownManager;
    private SoundPlayer soundPlayer;

    private boolean placeholderApiHooked = false;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.configManager.reload();
        this.messageManager.reload();

        String storageType = getConfig().getString("storage.type", "yml").toLowerCase();
        if ("mariadb".equals(storageType)) {
            try {
                this.dataManager = new MariaDBDataManager(this);
                this.dataManager.init();
                getLogger().info("MariaDB 저장소를 사용합니다.");
            } catch (Exception ex) {
                getLogger().severe("MariaDB 연결 실패, YML 로 폴백합니다: " + ex.getMessage());
                this.dataManager = new YmlDataManager(this);
                this.dataManager.init();
            }
        } else {
            this.dataManager = new YmlDataManager(this);
            this.dataManager.init();
            getLogger().info("YML 저장소를 사용합니다.");
            getLogger().warning("멀티서버(공유 DB) 환경에서는 storage.type=mariadb 를 사용해야 서버 간 닉네임이 동기화됩니다.");
        }

        this.ticketManager = new TicketManager(this);
        this.nicknameManager = new NicknameManager(this);
        this.nicknameAnvilGUI = new NicknameAnvilGUI(this);
        this.commandCooldownManager = new CommandCooldownManager(this);
        this.soundPlayer = new SoundPlayer(this);

        // 머리 위 닉네임 (PacketEvents 기반 PLAYER_INFO_UPDATE 가로채기)
        if (configManager.isNameTagEnabled() && isPacketEventsAvailable()) {
            try {
                this.nameTagPacketListener = new NameTagPacketListener(this);
                this.nameTagPacketListener.register();
                getLogger().info("머리 위 닉네임 기능 활성화 (PLAYER_INFO_UPDATE 가로채기).");
                getLogger().info("주의: Velocitab 의 remove_nametags 가 true 면 본 기능이 가려집니다 — false 로 설정하세요.");
            } catch (Throwable t) {
                getLogger().warning("머리 위 닉네임 초기화 실패 (PacketEvents API 호환성 확인 필요): " + t.getMessage());
                this.nameTagPacketListener = null;
            }
        } else if (configManager.isNameTagEnabled()) {
            getLogger().warning("name-tag.enabled = true 이지만 PacketEvents 플러그인이 없어 머리 위 닉네임 기능을 건너뜁니다.");
        }

        NicknameCommand command = new NicknameCommand(this);
        var pluginCommand = getCommand("닉네임");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(new NicknameTabCompleter(this));
        } else {
            getLogger().severe("/닉네임 명령어를 plugin.yml 에서 찾지 못했습니다!");
        }

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);

        if (pm.getPlugin("PlaceholderAPI") != null) {
            try {
                new NicknameExpansion(this).register();
                placeholderApiHooked = true;
                getLogger().info("PlaceholderAPI 확장 등록 완료. (%nickname_display% 등)");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI 확장 등록 실패: " + t.getMessage());
            }
        }

        Bukkit.getOnlinePlayers().forEach(p -> nicknameManager.refreshDisplay(p));
        getLogger().info("NicknamePlugin 활성화 완료.");
    }

    @Override
    public void onDisable() {
        if (nameTagPacketListener != null) {
            try {
                nameTagPacketListener.unregister();
            } catch (Exception ex) {
                getLogger().warning("머리 위 닉네임 패킷 리스너 해제 중 오류: " + ex.getMessage());
            }
        }
        if (dataManager != null) {
            try {
                dataManager.saveAll();
                dataManager.close();
            } catch (Exception ex) {
                getLogger().severe("데이터 저장 중 오류: " + ex.getMessage());
            }
        }
        getLogger().info("NicknamePlugin 비활성화.");
    }

    public void doReload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();

        // name-tag.enabled 토글에 따라 listener 등록/해제
        if (configManager.isNameTagEnabled() && isPacketEventsAvailable()) {
            if (nameTagPacketListener == null) {
                try {
                    this.nameTagPacketListener = new NameTagPacketListener(this);
                    this.nameTagPacketListener.register();
                } catch (Throwable t) {
                    getLogger().warning("리로드 중 머리 위 닉네임 초기화 실패: " + t.getMessage());
                    this.nameTagPacketListener = null;
                }
            }
        } else if (nameTagPacketListener != null) {
            nameTagPacketListener.unregister();
            nameTagPacketListener = null;
        }

        // 위장닉이 있는 온라인 플레이어를 viewer 들에게 다시 push
        if (nameTagPacketListener != null) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                if (nicknameManager.hasFake(p)) {
                    nameTagPacketListener.refreshForAllViewers(p);
                }
            }
        }
        Bukkit.getOnlinePlayers().forEach(p -> nicknameManager.refreshDisplay(p));
    }

    /** PacketEvents 플러그인이 설치/활성화 되어 있는지. */
    public boolean isPacketEventsAvailable() {
        var pm = Bukkit.getPluginManager();
        var pe = pm.getPlugin("packetevents");
        if (pe == null) pe = pm.getPlugin("PacketEvents");
        return pe != null && pe.isEnabled();
    }

    public static NicknamePlugin getInstance()              { return instance; }
    public ConfigManager getConfigManager()                 { return configManager; }
    public MessageManager getMessageManager()               { return messageManager; }
    public DataManager getDataManager()                     { return dataManager; }
    public NicknameManager getNicknameManager()             { return nicknameManager; }
    public TicketManager getTicketManager()                 { return ticketManager; }
    public NicknameAnvilGUI getNicknameAnvilGUI()           { return nicknameAnvilGUI; }
    public NameTagPacketListener getNameTagPacketListener() { return nameTagPacketListener; }
    public CommandCooldownManager getCommandCooldownManager() { return commandCooldownManager; }
    public SoundPlayer getSoundPlayer()                     { return soundPlayer; }
    public boolean isPlaceholderApiHooked()                 { return placeholderApiHooked; }
}
