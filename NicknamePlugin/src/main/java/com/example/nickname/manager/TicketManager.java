package com.example.nickname.manager;

import com.example.nickname.NicknamePlugin;
import com.example.nickname.util.ItemBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class TicketManager {

    private final NicknamePlugin plugin;
    private final NamespacedKey  key;

    public TicketManager(NicknamePlugin plugin) {
        this.plugin = plugin;
        this.key    = new NamespacedKey(plugin, "nickname_ticket");
    }

    /** 변경권 아이템 생성 (지정 수량) */
    public ItemStack create(int amount) {
        ItemStack stack = ItemBuilder.fromConfig(plugin.getConfig().getConfigurationSection("ticket"));
        stack.setAmount(Math.max(1, Math.min(64, amount)));

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 아이템이 변경권인지 확인 - PersistentDataContainer 태그로 판단 */
    public boolean isTicket(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(key, PersistentDataType.BYTE);
    }

    /** 인벤토리에 변경권이 1개 이상 있는지 */
    public boolean has(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isTicket(item)) return true;
        }
        return false;
    }

    /**
     * 인벤토리에서 변경권 1개 소모.
     * 성공 시 true, 변경권이 없으면 false.
     */
    public boolean consume(Player player) {
        Inventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (isTicket(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    storage[i] = null;
                }
                inv.setStorageContents(storage);
                return true;
            }
        }
        return false;
    }

    public NamespacedKey getKey() { return key; }
}
