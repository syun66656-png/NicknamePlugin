package com.example.nickname.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MiniMessage + {@code &#RRGGBB} + {@code &} 레거시 색/서식 코드를 함께 지원한다.
 */
public final class TextParser {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX_SHORT = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern HEX_LEGACY = Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");

    private static final Map<Character, String> LEGACY_COLORS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>")
    );

    private static final Map<Character, String> LEGACY_FORMATS = Map.of(
            'k', "<obfuscated>",
            'l', "<bold>",
            'm', "<strikethrough>",
            'n', "<underlined>",
            'o', "<italic>",
            'r', "<reset>"
    );

    private TextParser() {}

    public static Component parse(String input) {
        return parse(input, TagResolver.empty());
    }

    public static Component parse(String input, TagResolver resolver) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String prepared = prepare(input);
        try {
            return MM.deserialize(prepared, resolver);
        } catch (Exception ex) {
            return Component.text(prepared);
        }
    }

    public static Component parseNoItalic(String input) {
        return parse(input).decoration(TextDecoration.ITALIC, false);
    }

    public static String toPlain(String input) {
        return PlainTextComponentSerializer.plainText().serialize(parse(input));
    }

    private static String prepare(String input) {
        String s = input;
        s = convertHexShort(s);
        s = convertHexLegacy(s);
        s = convertLegacyAmpersand(s);
        return s;
    }

    private static String convertHexShort(String s) {
        Matcher m = HEX_SHORT.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, "<#" + m.group(1) + ">");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String convertHexLegacy(String s) {
        Matcher m = HEX_LEGACY.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hex = m.group(1).replace("&", "");
            m.appendReplacement(sb, "<#" + hex + ">");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String convertLegacyAmpersand(String s) {
        if (!s.contains("&")) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] != '&' || i + 1 >= chars.length) {
                out.append(chars[i]);
                continue;
            }
            char code = Character.toLowerCase(chars[i + 1]);
            if (code == 'x') {
                out.append(chars[i]).append(chars[i + 1]);
                i++;
                continue;
            }
            String color = LEGACY_COLORS.get(code);
            if (color != null) {
                out.append(color);
                i++;
                continue;
            }
            String format = LEGACY_FORMATS.get(code);
            if (format != null) {
                out.append(format);
                i++;
                continue;
            }
            out.append(chars[i]);
        }
        return out.toString();
    }
}
