package com.example.nickname.util;

import com.example.nickname.NicknamePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    private ItemBuilder() {}

    /**
     * ConfigurationSection 의 키:
     *   material:String, custom-model-data:int(optional),
     *   name:String(optional), lore:List<String>(optional), glint:boolean(optional)
     */
    public static ItemStack fromConfig(ConfigurationSection sec) {
        return fromConfig(sec, null, null);
    }

    /**
     * GUI 등 플레이어 기준 PlaceholderAPI({@code %img_maus%} 등) 가 필요할 때 사용.
     */
    public static ItemStack fromConfig(ConfigurationSection sec, Player player, NicknamePlugin plugin) {
        if (sec == null) return new ItemStack(Material.STONE);

        Material material = parseMaterial(sec.getString("material", "PAPER"), Material.PAPER);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String rawName = sec.getString("name", null);
        if (rawName != null && !rawName.isBlank()) {
            meta.displayName(TextParser.parseNoItalic(resolvePlaceholders(plugin, player, rawName)));
        }

        if (sec.isList("lore")) {
            List<String> rawLore = sec.getStringList("lore");
            if (!rawLore.isEmpty()) {
                List<Component> lore = new ArrayList<>(rawLore.size());
                for (String line : rawLore) {
                    lore.add(TextParser.parseNoItalic(resolvePlaceholders(plugin, player, line)));
                }
                meta.lore(lore);
            }
        }

        if (sec.contains("custom-model-data")) {
            int cmd = sec.getInt("custom-model-data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);
        }

        if (sec.getBoolean("glint", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String resolvePlaceholders(NicknamePlugin plugin, Player player, String text) {
        if (player == null || plugin == null) {
            return text;
        }
        return PlaceholderTexts.apply(plugin, player, text);
    }

    private static Material parseMaterial(String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name.toUpperCase());
        return m != null ? m : def;
    }
}
