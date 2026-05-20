package com.maris7.leaderboard.gui;

import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.model.LeaderboardCategory;
import com.maris7.leaderboard.model.LeaderboardEntry;
import com.maris7.leaderboard.model.PlayerViewState;
import com.maris7.leaderboard.service.LeaderboardService;
import com.maris7.leaderboard.util.ColorUtil;
import com.maris7.leaderboard.util.ItemBuilder;
import com.maris7.leaderboard.util.SchedulerUtil;
import com.maris7.leaderboard.util.SkullUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class LeaderboardGuiManager {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final LeaderboardService service;
    private final Map<UUID, PlayerViewState> boardStates = new ConcurrentHashMap<>();
    private final Set<UUID> mainMenuViewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> suppressNextPlayerClose = ConcurrentHashMap.newKeySet();

    public LeaderboardGuiManager(JavaPlugin plugin, PluginConfig config, LeaderboardService service) {
        this.plugin = plugin;
        this.config = config;
        this.service = service;
    }

    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new MarisGuiHolder(MarisGuiType.MAIN_MENU), config.mainRows() * 9, ColorUtil.title(ColorUtil.toSmallFont(config.mainTitle())));
        for (LeaderboardCategory category : config.categories()) {
            inventory.setItem(config.categorySlot(category), new ItemBuilder(config.categoryMaterial(category))
                    .name(config.categoryName(category))
                    .lore(config.categoryLore(category)).build());
        }
        // Open inventory first (see openBoard for explanation of why state is set after).
        player.openInventory(inventory);
        mainMenuViewers.add(player.getUniqueId());
        boardStates.remove(player.getUniqueId());
    }

    public void openBoard(Player player, LeaderboardCategory category, int page, List<LeaderboardEntry> entries, String searchQuery) {
        String title = config.boardTitle()
                .replace("{category}", ColorUtil.toSmallFont(category.displayName()))
                .replace("{page}", String.valueOf(page + 1));
        Inventory inventory = Bukkit.createInventory(new MarisGuiHolder(MarisGuiType.BOARD), config.boardRows() * 9, ColorUtil.title(title));

        int start = page * 45;
        int end = Math.min(entries.size(), start + 45);
        for (int slot = 0, index = start; index < end; index++, slot++) {
            LeaderboardEntry entry = entries.get(index);
            inventory.setItem(slot, entryItem(category, entry));
        }

        if (page > 0) inventory.setItem(config.previousSlot(), new ItemBuilder(config.previousMaterial()).name(config.previousName()).lore(config.previousLore()).build());
        if (end < entries.size()) inventory.setItem(config.nextSlot(), new ItemBuilder(config.nextMaterial()).name(config.nextName()).lore(config.nextLore()).build());
        inventory.setItem(config.refreshSlot(), new ItemBuilder(config.categoryMaterial(category))
                .name(config.refreshName().replace("{category}", ColorUtil.toSmallFont(category.displayName())))
                .lore(config.refreshLore().stream()
                        .map(line -> line.replace("{category}", ColorUtil.toSmallFont(category.displayName())))
                        .toList())
                .build());
        inventory.setItem(config.searchSlot(), new ItemBuilder(config.searchMaterial()).name(config.searchName()).lore(config.searchLore()).build());

        service.self(category, player.getUniqueId()).thenAccept(optional -> optional.ifPresent(self -> SchedulerUtil.runPlayer(plugin, player,
                () -> inventory.setItem(config.selfSlot(), selfItem(category, self)))));

        // Open the inventory FIRST before updating state maps.
        // player.openInventory() synchronously fires InventoryCloseEvent for the previously
        // open inventory, which triggers onInventoryClose → clearView() → clears boardStates.
        // If we put state into the map before openInventory, clearView wipes it immediately.
        // By opening first, clearView removes the OLD state, and we set the NEW state after.
        player.openInventory(inventory);
        boardStates.put(player.getUniqueId(), new PlayerViewState(category, page, searchQuery, entries));
        mainMenuViewers.remove(player.getUniqueId());
    }

    public boolean isPluginInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MarisGuiHolder;
    }

    public boolean isMainMenuInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MarisGuiHolder holder && holder.type() == MarisGuiType.MAIN_MENU;
    }

    private ItemStack entryItem(LeaderboardCategory category, LeaderboardEntry entry) {
        String name = config.entryName().replace("{player}", entry.playerName());
        List<String> lore = config.entryLore().stream()
                .map(line -> line.replace("{category}", category.displayName()).replace("{value}", service.displayValue(category, entry)).replace("{rank}", String.valueOf(entry.rank())))
                .toList();
        return SkullUtil.playerHead(entry.uniqueId(), entry.playerName(), name, lore);
    }

    private ItemStack selfItem(LeaderboardCategory category, LeaderboardEntry entry) {
        String name = config.selfName().replace("{player}", entry.playerName());
        List<String> lore = config.selfLore().stream()
                .map(line -> line.replace("{category}", category.displayName()).replace("{value}", service.displayValue(category, entry)).replace("{rank}", String.valueOf(entry.rank())))
                .toList();
        return SkullUtil.playerHead(entry.uniqueId(), entry.playerName(), name, lore);
    }

    public boolean isMainMenu(Player player) { return mainMenuViewers.contains(player.getUniqueId()); }
    public Optional<PlayerViewState> state(Player player) { return Optional.ofNullable(boardStates.get(player.getUniqueId())); }
    public void returnToLastBoard(Player player) {
        PlayerViewState state = boardStates.get(player.getUniqueId());
        if (state == null) {
            openMainMenu(player);
            return;
        }
        openBoard(player, state.category(), state.page(), state.filteredEntries(), state.searchQuery());
    }

    public void clearView(Player player) {
        mainMenuViewers.remove(player.getUniqueId());
        boardStates.remove(player.getUniqueId());
        suppressNextPlayerClose.remove(player.getUniqueId());
    }

    public void suppressNextPlayerClose(Player player) {
        suppressNextPlayerClose.add(player.getUniqueId());
    }

    public boolean consumeSuppressedPlayerClose(Player player) {
        return suppressNextPlayerClose.remove(player.getUniqueId());
    }

    public List<LeaderboardEntry> entriesFor(LeaderboardCategory category) {
        return service.top(category);
    }

    public void playSound(Player player, String key, Sound fallback, float fallbackVolume, float fallbackPitch) {
        if (!config.soundEnabled(key)) {
            return;
        }
        Sound sound = config.sound(key);
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, config.soundVolume(key, fallbackVolume), config.soundPitch(key, fallbackPitch));
    }

    private record MarisGuiHolder(MarisGuiType type) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private enum MarisGuiType {
        MAIN_MENU,
        BOARD
    }
}
