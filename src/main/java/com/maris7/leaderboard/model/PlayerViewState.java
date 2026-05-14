package com.maris7.leaderboard.model;

import java.util.List;

public final class PlayerViewState {
    private final LeaderboardCategory category;
    private int page;
    private String searchQuery;
    private List<LeaderboardEntry> filteredEntries;

    public PlayerViewState(LeaderboardCategory category, int page, String searchQuery, List<LeaderboardEntry> filteredEntries) {
        this.category = category;
        this.page = page;
        this.searchQuery = searchQuery;
        this.filteredEntries = filteredEntries;
    }

    public LeaderboardCategory category() { return category; }
    public int page() { return page; }
    public void page(int page) { this.page = page; }
    public String searchQuery() { return searchQuery; }
    public void searchQuery(String searchQuery) { this.searchQuery = searchQuery; }
    public List<LeaderboardEntry> filteredEntries() { return filteredEntries; }
    public void filteredEntries(List<LeaderboardEntry> filteredEntries) { this.filteredEntries = filteredEntries; }
}
