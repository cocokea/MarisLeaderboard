package com.maris7.leaderboard.listener;

import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.gui.LeaderboardGuiManager;
import com.maris7.leaderboard.model.LeaderboardCategory;
import com.maris7.leaderboard.model.PlayerViewState;
import com.maris7.leaderboard.service.LeaderboardService;
import com.maris7.leaderboard.service.SignSearchService;
import com.maris7.leaderboard.util.SchedulerUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryListener implements Listener {
    private final LeaderboardGuiManager guiManager;
    private final LeaderboardService service;
    private final SignSearchService signSearchService;
    private final PluginConfig config;
    private final Map<UUID, Long> clickDebounce = new ConcurrentHashMap<>();

    public InventoryListener(LeaderboardGuiManager guiManager, LeaderboardService service, SignSearchService signSearchService, PluginConfig config) {
        this.guiManager = guiManager;
        this.service = service;
        this.signSearchService = signSearchService;
        this.config = config;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!guiManager.isPluginInventory(event.getView().getTopInventory())) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = clickDebounce.put(player.getUniqueId(), now);
        if (last != null && (now - last) < config.debounceClickMs()) return;

        int slot = event.getRawSlot();
        if (guiManager.isMainMenuInventory(event.getView().getTopInventory())) {
            for (LeaderboardCategory category : config.categories()) {
                if (slot == config.categorySlot(category)) {
                    guiManager.playSound(player, "click", Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
                    guiManager.openBoard(player, category, 0, guiManager.entriesFor(category), null);
                    return;
                }
            }
            return;
        }

        PlayerViewState state = guiManager.state(player).orElse(null);
        if (state == null) return;

        if (slot == config.previousSlot() && state.page() > 0) {
            guiManager.playSound(player, "click", Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            guiManager.openBoard(player, state.category(), state.page() - 1, state.filteredEntries(), state.searchQuery());
            return;
        }
        if (slot == config.nextSlot() && ((state.page() + 1) * 45) < state.filteredEntries().size()) {
            guiManager.playSound(player, "click", Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            guiManager.openBoard(player, state.category(), state.page() + 1, state.filteredEntries(), state.searchQuery());
            return;
        }
        if (slot == config.refreshSlot()) {
            guiManager.playSound(player, "refresh", Sound.BLOCK_ANVIL_USE, 1.0f, 1.1f);
            service.forceRefresh(player);
            String query = state.searchQuery();
            if (query != null && !query.isBlank()) {
                service.search(state.category(), query).thenAccept(result ->
                        SchedulerUtil.runPlayer(config.plugin(), player, () ->
                                guiManager.openBoard(player, state.category(), 0, result, query)));
            } else {
                guiManager.openBoard(player, state.category(), state.page(), guiManager.entriesFor(state.category()), null);
            }
            return;
        }
        if (slot == config.searchSlot()) {
            guiManager.suppressNextPlayerClose(player);
            // Delay closeInventory to next tick — calling it directly inside InventoryClickEvent
            // only closes the inventory server-side; the client never receives the close packet
            // properly, so the GUI stays visible on the client. Scheduling for the next tick
            // ensures the click event finishes first, then the server sends the close packet.
            SchedulerUtil.runPlayer(config.plugin(), player, () -> {
                player.closeInventory();
                signSearchService.open(player, query ->
                        service.search(state.category(), query).thenAccept(result ->
                                SchedulerUtil.runPlayer(config.plugin(), player, () ->
                                        guiManager.openBoard(player, state.category(), 0, result, query))));
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!guiManager.isPluginInventory(event.getInventory())) {
            return;
        }
        if (signSearchService.isWaiting(player)) {
            return;
        }
        boolean reopen = false;
        if (!guiManager.consumeSuppressedPlayerClose(player)) {
            try {
                reopen = event.getReason() == InventoryCloseEvent.Reason.PLAYER && !guiManager.isMainMenuInventory(event.getInventory());
            } catch (NoSuchMethodError ignored) {
                reopen = false;
            }
        }
        guiManager.clearView(player);
        if (reopen) {
            SchedulerUtil.runPlayer(config.plugin(), player, () -> guiManager.openMainMenu(player));
        }
    }

}
