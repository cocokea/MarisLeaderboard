package com.maris7.leaderboard.data;

import com.maris7.leaderboard.config.PluginConfig;
import com.maris7.leaderboard.model.LeaderboardCategory;
import com.maris7.leaderboard.model.LeaderboardEntry;
import com.maris7.leaderboard.model.PlayerLeaderboardData;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public final class LeaderboardRepository {
    private final DatabaseManager databaseManager;
    private final PluginConfig config;

    public LeaderboardRepository(DatabaseManager databaseManager, PluginConfig config) {
        this.databaseManager = databaseManager;
        this.config = config;
    }

    public void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + config.table() + " (uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16) NOT NULL, updated_at BIGINT)";
        try (Connection connection = databaseManager.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
            ensureCategoryColumns(connection, config.categories());
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void ensureCategoryColumns(Connection connection, List<LeaderboardCategory> categories) throws SQLException {
        Set<String> existing = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), null, config.table(), null)) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        try (Statement statement = connection.createStatement()) {
            for (LeaderboardCategory category : categories) {
                String rawColumn = column(category, "raw");
                String numColumn = column(category, "num");
                if (!existing.contains(rawColumn.toLowerCase(Locale.ROOT))) {
                    statement.executeUpdate("ALTER TABLE " + config.table() + " ADD COLUMN " + rawColumn + " TEXT");
                }
                if (!existing.contains(numColumn.toLowerCase(Locale.ROOT))) {
                    statement.executeUpdate("ALTER TABLE " + config.table() + " ADD COLUMN " + numColumn + " DOUBLE DEFAULT 0");
                }
                try {
                    statement.executeUpdate("CREATE INDEX idx_" + config.table() + "_" + category.key() + " ON " + config.table() + "(" + numColumn + " DESC, player_name ASC)");
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public void upsert(PlayerLeaderboardData data, List<LeaderboardCategory> categories) {
        try (Connection connection = databaseManager.connection(); PreparedStatement statement = connection.prepareStatement(buildUpsertSql(categories))) {
            bindUpsert(statement, data, categories);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void upsertBatch(List<PlayerLeaderboardData> batch, List<LeaderboardCategory> categories) {
        if (batch.isEmpty()) {
            return;
        }
        try (Connection connection = databaseManager.connection(); PreparedStatement statement = connection.prepareStatement(buildUpsertSql(categories))) {
            connection.setAutoCommit(false);
            try {
                for (PlayerLeaderboardData data : batch) {
                    bindUpsert(statement, data, categories);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw ex;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private String buildUpsertSql(List<LeaderboardCategory> categories) {
        List<String> columns = new ArrayList<>(List.of("uuid", "player_name", "updated_at"));
        for (LeaderboardCategory category : categories) {
            columns.add(column(category, "raw"));
            columns.add(column(category, "num"));
        }
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String updates;
        if (config.dbType().equalsIgnoreCase("MYSQL")) {
            updates = columns.stream().filter(c -> !c.equals("uuid")).map(c -> c + "=VALUES(" + c + ")").collect(Collectors.joining(", "));
            return "INSERT INTO " + config.table() + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ") ON DUPLICATE KEY UPDATE " + updates;
        }
        updates = columns.stream().filter(c -> !c.equals("uuid")).map(c -> c + "=excluded." + c).collect(Collectors.joining(", "));
        return "INSERT INTO " + config.table() + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ") ON CONFLICT(uuid) DO UPDATE SET " + updates;
    }

    private void bindUpsert(PreparedStatement statement, PlayerLeaderboardData data, List<LeaderboardCategory> categories) throws SQLException {
        int index = 1;
        statement.setString(index++, data.uniqueId().toString());
        statement.setString(index++, data.name());
        statement.setLong(index++, data.updatedAt());
        for (LeaderboardCategory category : categories) {
            statement.setString(index++, data.rawValues().getOrDefault(category.key(), "0"));
            statement.setDouble(index++, data.numericValues().getOrDefault(category.key(), 0D));
        }
    }

    public List<LeaderboardEntry> top(LeaderboardCategory category, int limit) {
        String sql = "SELECT uuid, player_name, " + column(category, "num") + ", " + column(category, "raw") + " FROM " + config.table() + " ORDER BY " + column(category, "num") + " DESC, player_name ASC LIMIT ?";
        return entries(sql, ps -> ps.setInt(1, limit));
    }

    public List<LeaderboardEntry> search(LeaderboardCategory category, String query, int limit) {
        String sql = "SELECT uuid, player_name, " + column(category, "num") + ", " + column(category, "raw") + " FROM " + config.table() + " WHERE LOWER(player_name) LIKE ? ORDER BY " + column(category, "num") + " DESC, player_name ASC LIMIT ?";
        return entries(sql, ps -> {
            ps.setString(1, "%" + query.toLowerCase(Locale.ROOT) + "%");
            ps.setInt(2, limit);
        });
    }

    public Optional<LeaderboardEntry> self(LeaderboardCategory category, UUID uuid) {
        String numColumn = column(category, "num");
        String rawColumn = column(category, "raw");
        String sql = "SELECT base.uuid, base.player_name, base." + numColumn + ", base." + rawColumn + ", (SELECT COUNT(*) + 1 FROM " + config.table() + " ranked WHERE ranked." + numColumn + " > base." + numColumn + ") AS rank FROM " + config.table() + " base WHERE base.uuid = ?";
        try (Connection connection = databaseManager.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new LeaderboardEntry(UUID.fromString(rs.getString("uuid")), rs.getString("player_name"), rs.getDouble(3), rs.getString(4), rs.getInt("rank")));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return Optional.empty();
    }

    private String column(LeaderboardCategory category, String suffix) {
        return category.key() + "_" + suffix;
    }

    private List<LeaderboardEntry> entries(String sql, SqlConsumer consumer) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = databaseManager.connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            consumer.accept(statement);
            try (ResultSet rs = statement.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    entries.add(new LeaderboardEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getDouble(3), rs.getString(4), rank++));
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        return entries;
    }

    @FunctionalInterface
    private interface SqlConsumer { void accept(PreparedStatement statement) throws SQLException; }
}
