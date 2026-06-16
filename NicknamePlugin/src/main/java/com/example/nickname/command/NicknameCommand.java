package com.example.nickname.command;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.config.MessageManager;
import com.example.nickname.data.NicknameData;
import com.example.nickname.manager.CommandCooldownManager;
import com.example.nickname.manager.NicknameManager;
import com.example.nickname.manager.TicketManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NicknameCommand implements CommandExecutor {

    private final NicknamePlugin plugin;

    public NicknameCommand(NicknamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        MessageManager msg = plugin.getMessageManager();

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                msg.send(sender, "common.player-only");
                return true;
            }
            if (!p.hasPermission("nickname.use")) {
                msg.send(p, "common.no-permission");
                return true;
            }
            if (!tryCommandCooldown(sender)) {
                return true;
            }
            plugin.getNicknameAnvilGUI().open(p);
            return true;
        }

        String sub = args[0];

        if (sub.equalsIgnoreCase("reload") || sub.equals("리로드")) {
            if (!sender.hasPermission("nickname.admin")) {
                msg.send(sender, "common.no-permission");
                return true;
            }
            plugin.doReload();
            msg.send(sender, "common.reloaded");
            return true;
        }

        switch (sub) {
            case "확인" -> handleCheck(sender, args);
            case "지급" -> handleGive(sender, args);
            case "초기화" -> handleReset(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleCheck(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        NicknameManager nm = plugin.getNicknameManager();

        if (!sender.hasPermission("nickname.check")) {
            msg.send(sender, "common.no-permission");
            return;
        }
        if (!tryCommandCooldown(sender)) {
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "common.usage-check");
            return;
        }

        String name = args[1];

        Optional<NicknameData> byFake = plugin.getDataManager().getByFakeName(name);
        if (byFake.isPresent()) {
            NicknameData d = byFake.get();
            msg.send(sender, "check.by-fake",
                    MessageManager.of("fake", d.getFakeName(), "real", d.getRealName()));
            return;
        }

        Optional<NicknameData> data = nm.findByAny(name);
        if (data.isEmpty()) {
            msg.send(sender, "common.player-not-found");
            return;
        }
        NicknameData d = data.get();
        if (d.hasFakeName()) {
            msg.send(sender, "check.has-fake",
                    MessageManager.of("player", d.getRealName(), "fake", d.getFakeName()));
        } else {
            msg.send(sender, "check.no-fake",
                    MessageManager.of("player", d.getRealName()));
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();

        if (!sender.hasPermission("nickname.admin")) {
            msg.send(sender, "common.no-permission");
            return;
        }
        if (!tryCommandCooldown(sender)) {
            return;
        }
        if (args.length < 3) {
            msg.send(sender, "common.usage-give");
            return;
        }

        String name = args[1];
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            msg.send(sender, "give.invalid-amount");
            return;
        }
        if (amount <= 0) {
            msg.send(sender, "give.invalid-amount");
            return;
        }

        Player target = null;
        Optional<NicknameData> fake = plugin.getDataManager().getByFakeName(name);
        if (fake.isPresent()) {
            target = Bukkit.getPlayer(fake.get().getUuid());
        }
        if (target == null) {
            target = Bukkit.getPlayerExact(name);
        }
        if (target == null) {
            msg.send(sender, "give.not-online");
            return;
        }

        TicketManager tm = plugin.getTicketManager();
        int remaining = amount;
        int dropped = 0;
        while (remaining > 0) {
            int give = Math.min(64, remaining);
            ItemStack ticket = tm.create(give);
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(ticket);
            if (!leftover.isEmpty()) {
                for (ItemStack ls : leftover.values()) {
                    target.getWorld().dropItem(target.getLocation(), ls);
                    dropped += ls.getAmount();
                }
            }
            remaining -= give;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.getName());
        ph.put("amount", String.valueOf(amount));
        msg.send(sender, "give.success", ph);

        if (dropped > 0) {
            Map<String, String> ph2 = new HashMap<>();
            ph2.put("player", target.getName());
            ph2.put("amount", String.valueOf(dropped));
            msg.send(sender, "give.inventory-full", ph2);
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        MessageManager msg = plugin.getMessageManager();
        NicknameManager nm = plugin.getNicknameManager();

        if (!sender.hasPermission("nickname.admin")) {
            msg.send(sender, "common.no-permission");
            return;
        }
        if (!tryCommandCooldown(sender)) {
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "common.usage-reset");
            return;
        }

        String name = args[1];

        Optional<NicknameData> byFake = plugin.getDataManager().getByFakeName(name);
        if (byFake.isPresent()) {
            NicknameData d = byFake.get();
            String real = d.getRealName();
            String fake = d.getFakeName();
            UUID uuid = d.getUuid();
            nm.resetNickname(uuid);
            msg.send(sender, "reset.by-fake",
                    MessageManager.of("fake", fake, "real", real));
            return;
        }

        Optional<NicknameData> data = nm.findByAny(name);
        if (data.isEmpty()) {
            msg.send(sender, "common.player-not-found");
            return;
        }
        NicknameData d = data.get();
        if (!d.hasFakeName()) {
            msg.send(sender, "reset.by-real-no-fake",
                    MessageManager.of("player", d.getRealName()));
            return;
        }
        String oldFake = d.getFakeName();
        nm.resetNickname(d.getUuid());
        msg.send(sender, "reset.by-real-has-fake",
                MessageManager.of("player", d.getRealName(), "fake", oldFake));
    }

    private void sendUsage(CommandSender sender) {
        MessageManager msg = plugin.getMessageManager();
        msg.send(sender, "common.usage-main");
        msg.send(sender, "common.usage-check");
        if (sender.hasPermission("nickname.admin")) {
            msg.send(sender, "common.usage-give");
            msg.send(sender, "common.usage-reset");
        }
    }

    private boolean tryCommandCooldown(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        CommandCooldownManager cooldown = plugin.getCommandCooldownManager();
        if (cooldown.tryUse(player)) {
            return true;
        }
        plugin.getMessageManager().send(player, "common.cooldown",
                MessageManager.of("seconds", String.valueOf(cooldown.getRemainingSeconds(player))));
        return false;
    }
}
