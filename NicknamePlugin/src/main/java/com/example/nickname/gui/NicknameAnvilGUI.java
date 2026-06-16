package com.example.nickname.gui;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.config.ConfigManager;
import com.example.nickname.config.MessageManager;
import com.example.nickname.config.SoundPlayer;
import com.example.nickname.manager.NicknameManager;
import com.example.nickname.manager.TicketManager;
import com.example.nickname.util.ItemBuilder;
import com.example.nickname.util.PlaceholderTexts;
import com.example.nickname.util.TextParser;
import com.example.nickname.libs.anvilgui.AnvilGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 닉네임 변경용 가상 모루.
 * Sytm/md5lukas AnvilGUI fork 사용 — snapshot.text() 로 rename 필드 읽음 (Java Record 접근자).
 */
public class NicknameAnvilGUI {

    private final NicknamePlugin plugin;

    public NicknameAnvilGUI(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    /** 공백·제로폭 문자 제거 후 trim. */
    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.replaceAll("[\\u200B-\\u200D\\uFEFF\\u00A0]", "");
        return stripped.trim();
    }

    private List<AnvilGUI.ResponseAction> closeThen(
            Player player,
            String messageKey,
            Map<String, String> placeholders,
            String soundKey) {
        MessageManager messages = plugin.getMessageManager();
        SoundPlayer sounds = plugin.getSoundPlayer();
        return Arrays.asList(
                AnvilGUI.ResponseAction.close(),
                AnvilGUI.ResponseAction.run(() -> {
                    sounds.play(player, soundKey);
                    messages.send(player, messageKey, placeholders);
                }));
    }

    public void open(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        MessageManager msg = plugin.getMessageManager();
        NicknameManager nm = plugin.getNicknameManager();
        TicketManager tm = plugin.getTicketManager();
        SoundPlayer sounds = plugin.getSoundPlayer();

        ConfigurationSection s0 = cfg.raw().getConfigurationSection("gui.slot-0");
        ConfigurationSection s1 = cfg.raw().getConfigurationSection("gui.slot-1");
        ConfigurationSection s2 = cfg.raw().getConfigurationSection("gui.slot-2");

        String rawTitle = cfg.raw().getString("gui.title", "<dark_gray>닉네임 변경</dark_gray>");
        String plainTitle = TextParser.toPlain(PlaceholderTexts.apply(plugin, player, rawTitle));

        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(plainTitle)
                .text("")
                .itemLeft(ItemBuilder.fromConfig(s0, player, plugin))
                .itemRight(ItemBuilder.fromConfig(s1, player, plugin))
                .itemOutput(ItemBuilder.fromConfig(s2, player, plugin))
                .onClick((slotIndex, snapshot) -> {
                    Player p = player;

                    if (slotIndex == AnvilGUI.Slot.INPUT_LEFT) {
                        return Collections.emptyList();
                    }

                    if (slotIndex == AnvilGUI.Slot.INPUT_RIGHT) {
                        if (!nm.hasFakeName(p.getUniqueId())) {
                            return closeThen(p, "gui.self-reset-no-fake", null, "reset-failed");
                        }
                        nm.resetNickname(p.getUniqueId());
                        return Arrays.asList(
                                AnvilGUI.ResponseAction.close(),
                                AnvilGUI.ResponseAction.run(() -> {
                                    sounds.play(p, "reset-success");
                                    msg.send(p, "gui.self-reset");
                                }));
                    }

                    if (slotIndex == AnvilGUI.Slot.OUTPUT) {
                        // Sytm fork (Java Record): snapshot.text() = 모루 rename 필드 현재 입력값
                        String input = sanitize(snapshot.text());

                        NicknameManager.SetResult result = nm.validate(p, input);
                        switch (result) {
                            case INVALID_FORMAT -> {
                                return closeThen(p, "gui.invalid-nickname", MessageManager.of(
                                        "min", String.valueOf(cfg.getMinLength()),
                                        "max", String.valueOf(cfg.getMaxLength())), "change-failed");
                            }
                            case SAME_AS_CURRENT -> {
                                return closeThen(p, "gui.nickname-same-as-current", null, "change-failed");
                            }
                            case TAKEN -> {
                                return closeThen(p, "gui.nickname-taken", null, "change-failed");
                            }
                            case IS_OTHER_REAL_NAME -> {
                                return closeThen(p, "gui.nickname-is-real-name", null, "change-failed");
                            }
                            case OK -> { /* pass */ }
                        }

                        if (!tm.has(p)) {
                            return closeThen(p, "gui.no-ticket", null, "change-failed");
                        }
                        if (!tm.consume(p)) {
                            return closeThen(p, "gui.no-ticket", null, "change-failed");
                        }

                        nm.applyFakeName(p, input);
                        return Arrays.asList(
                                AnvilGUI.ResponseAction.close(),
                                AnvilGUI.ResponseAction.run(() -> {
                                    sounds.play(p, "change-success");
                                    msg.send(p, "gui.self-changed", MessageManager.of("nickname", input));
                                }));
                    }

                    return Collections.emptyList();
                })
                .open(player);
    }
}
