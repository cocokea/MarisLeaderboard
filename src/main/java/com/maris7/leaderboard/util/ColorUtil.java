package com.maris7.leaderboard.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer AMPERSAND_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Map<Character, Character> SMALL_FONT = new HashMap<>();

    static {
        String normal = "abcdefghijklmnopqrstuvwxyz";
        String small = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘqʀꜱᴛᴜᴠᴡxʏᴢ";
        for (int i = 0; i < normal.length(); i++) {
            char normalChar = normal.charAt(i);
            char smallChar = small.charAt(i);
            SMALL_FONT.put(normalChar, smallChar);
            SMALL_FONT.put(Character.toUpperCase(normalChar), smallChar);
        }
    }

    private ColorUtil() {}

    private static String expandHexToAmpersand(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String colorizeLegacy(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', expandHexToAmpersand(input));
    }

    public static String color(String input) {
        return colorizeLegacy(input);
    }

    public static Component component(String input) {
        return AMPERSAND_SERIALIZER.deserialize(expandHexToAmpersand(input == null ? "" : input))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Component title(String input) {
        String plain = ChatColor.stripColor(colorizeLegacy(input == null ? "" : input));
        return Component.text(plain == null ? "" : plain)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static String stripColor(String input) {
        return ChatColor.stripColor(colorizeLegacy(input == null ? "" : input));
    }

    public static String toSmallFont(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            out.append(SMALL_FONT.getOrDefault(c, c));
        }
        return out.toString();
    }

    public static List<String> colorize(List<String> lines) {
        return lines.stream().map(ColorUtil::colorizeLegacy).collect(Collectors.toList());
    }
}
