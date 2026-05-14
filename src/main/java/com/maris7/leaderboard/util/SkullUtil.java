package com.maris7.leaderboard.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public final class SkullUtil {
    private SkullUtil() {}

    public static ItemStack playerHead(UUID uuid, String fallbackName, String displayName, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        // Use UUID-only lookup. Avoid Bukkit.getOfflinePlayer(String), which can block on
        // name -> UUID resolution while rendering leaderboard pages on the main/region thread.
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(ColorUtil.component(displayName));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        skull.setItemMeta(meta);
        return skull;
    }
}
