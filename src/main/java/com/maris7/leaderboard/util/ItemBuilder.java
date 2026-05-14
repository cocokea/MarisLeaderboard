package com.maris7.leaderboard.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemBuilder {
    private final ItemStack itemStack;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material == null ? Material.PAPER : material);
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(ColorUtil.component(name));
        itemStack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        ItemMeta meta = itemStack.getItemMeta();
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        itemStack.setItemMeta(meta);
        return this;
    }

    public ItemStack build() { return itemStack; }
}
