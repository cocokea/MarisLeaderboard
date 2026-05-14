package com.maris7.leaderboard.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.maris7.leaderboard.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(config.poolName());
        hikariConfig.setMaximumPoolSize(config.maxPoolSize());
        hikariConfig.setMinimumIdle(config.minIdle());
        hikariConfig.setMaxLifetime(config.maxLifetime());
        hikariConfig.setKeepaliveTime(config.keepAlive());
        hikariConfig.setConnectionTimeout(config.connectionTimeout());
        hikariConfig.setValidationTimeout(config.validationTimeout());
        hikariConfig.setLeakDetectionThreshold(config.leakDetectionThreshold());

        if (config.dbType().equalsIgnoreCase("MYSQL")) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.mysqlHost() + ":" + config.mysqlPort() + "/" + config.mysqlDatabase() + "?" + config.mysqlParameters());
            hikariConfig.setUsername(config.mysqlUsername());
            hikariConfig.setPassword(config.mysqlPassword());
        } else {
            File file = new File(config.sqliteFile());
            if (!file.isAbsolute()) file = new File(plugin.getServer().getWorldContainer(), config.sqliteFile());
            file.getParentFile().mkdirs();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hikariConfig.setConnectionTestQuery("SELECT 1");
        }
        dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
