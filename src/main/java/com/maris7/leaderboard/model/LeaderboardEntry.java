package com.maris7.leaderboard.model;

import java.util.UUID;

public record LeaderboardEntry(UUID uniqueId, String playerName, double numericValue, String displayValue, int rank) {
}
