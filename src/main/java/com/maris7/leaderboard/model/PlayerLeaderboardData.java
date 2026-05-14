package com.maris7.leaderboard.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerLeaderboardData {
    private final UUID uniqueId;
    private final String name;
    private final Map<String, String> rawValues = new HashMap<>();
    private final Map<String, Double> numericValues = new HashMap<>();
    private long updatedAt;

    public PlayerLeaderboardData(UUID uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
    }

    public UUID uniqueId() { return uniqueId; }
    public String name() { return name; }
    public Map<String, String> rawValues() { return rawValues; }
    public Map<String, Double> numericValues() { return numericValues; }
    public long updatedAt() { return updatedAt; }
    public void updatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
