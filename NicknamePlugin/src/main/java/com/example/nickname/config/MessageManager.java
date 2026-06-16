package com.example.nickname.config;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.util.PlaceholderTexts;
import com.example.nickname.util.TextParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageManager {

    private final NicknamePlugin plugin;

    private FileConfiguration messages;
    private String prefix = "";

    public MessageManager(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);

        this.messages = YamlConfiguration.loadConfiguration(file);

        try (var defReader = new InputStreamReader(plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(defReader));
        } catch (Exception ignored) { /* bundled default optional */ }

        this.prefix = messages.getString("prefix", "");
    }

    public String raw(String key) {
        return messages.getString(key, "<red>missing message: " + key + "</red>");
    }

    public List<String> rawList(String key) {
        return messages.getStringList(key);
    }

    public Component build(CommandSender audience, String key, Map<String, String> placeholders) {
        return parse(applyExternal(audience, raw(key)), placeholders);
    }

    public Component build(String key, Map<String, String> placeholders) {
        return parse(applyExternal(null, raw(key)), placeholders);
    }

    public Component build(String key) {
        return build(key, null);
    }

    /**
     * MiniMessage + &#RRGGBB + & 레거시 + {@code <prefix>} 등 플러그인 변수.
     * PlaceholderAPI({@code %...%}) 는 {@link #applyExternal} 으로 먼저 치환할 것.
     */
    public Component parse(String raw, Map<String, String> placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(Placeholder.parsed("prefix", prefix == null ? "" : prefix));
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                if (e.getValue() != null) {
                    builder.resolver(Placeholder.parsed(e.getKey(), e.getValue()));
                }
            }
        }
        return TextParser.parse(raw, builder.build());
    }

    public Component parse(String raw) {
        return parse(raw, null);
    }

    public void send(CommandSender to, String key) {
        send(to, key, null);
    }

    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        if (to == null) return;
        String raw = raw(key);
        if (raw == null || raw.isEmpty() || "none".equalsIgnoreCase(raw)) return;
        to.sendMessage(parse(applyExternal(to, raw), placeholders));
    }

    /** PlaceholderAPI {@code %img_info%} 등 (수신자가 플레이어일 때 해당 플레이어 기준) */
    private String applyExternal(CommandSender audience, String text) {
        return PlaceholderTexts.apply(plugin, audience, text);
    }

    public static Map<String, String> of(String k, String v) {
        Map<String, String> m = new HashMap<>(2);
        m.put(k, v);
        return m;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new HashMap<>(4);
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, String> m = new HashMap<>(6);
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }

    public String getPrefix() {
        return prefix;
    }
}
