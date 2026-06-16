package com.example.nickname.command;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.data.NicknameData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * /닉네임 명령어 탭 자동완성.
 */
public class NicknameTabCompleter implements TabCompleter {

    private final NicknamePlugin plugin;

    private static final List<String> SUB_BASE = List.of("확인");
    private static final List<String> SUB_ADMIN = List.of("지급", "초기화", "reload");

    public NicknameTabCompleter(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUB_BASE);
            if (sender.hasPermission("nickname.admin")) {
                subs.addAll(SUB_ADMIN);
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0];
            return switch (sub) {
                case "확인", "지급", "초기화" -> filter(onlineNicknameTargets(), args[1]);
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && "지급".equalsIgnoreCase(args[0])) {
            return filter(Arrays.asList("1", "16", "32", "64"), args[2]);
        }

        return Collections.emptyList();
    }

    /** 온라인 플레이어 진짜 닉 + 위장 닉(있을 때) */
    private List<String> onlineNicknameTargets() {
        Set<String> names = new LinkedHashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
            plugin.getDataManager().getByUuid(p.getUniqueId())
                    .filter(NicknameData::hasFakeName)
                    .map(NicknameData::getFakeName)
                    .ifPresent(names::add);
        }
        return new ArrayList<>(names);
    }

    private List<String> filter(List<String> source, String partial) {
        if (partial == null || partial.isEmpty()) return source;
        String p = partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(source.size());
        for (String s : source) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) out.add(s);
        }
        return out;
    }
}
