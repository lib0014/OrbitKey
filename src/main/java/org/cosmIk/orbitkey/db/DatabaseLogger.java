package org.cosmIk.orbitkey.db;

import org.cosmIk.orbitkey.OrbitKey;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

public class DatabaseLogger {
    private final OrbitKey plugin;
    private final String connectionUrl;

    public DatabaseLogger(OrbitKey plugin) {
        this.plugin = plugin;
        // Путь к файлу локальной БД H2 в папке плагина
        File dbFile = new File(plugin.getDataFolder(), "logs_db");
        this.connectionUrl = "jdbc:h2:" + dbFile.getAbsolutePath() + ";AUTO_SERVER=TRUE";

        initializeTable();
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionUrl, "sa", "");
    }

    private void initializeTable() {
        // Создаем папку плагина, если её нет, чтобы H2 смог создать файл
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS orbit_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "log_level INT, " +
                    "message VARCHAR(1000)" +
                    ");";
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось инициализировать таблицу логирования H2: " + e.getMessage());
        }
    }

    /**
     * Асинхронно записывает лог в базу данных, чтобы не блокировать основной поток сервера
     */
    public void logAsync(int level, String message) {
        var cfg = plugin.getConfigManager();
        if (cfg.getLoggerMode() < level) return;

        // Очищаем сообщение от кодов MiniMessage/Legacy для чистого текста в БД
        String cleanMessage = message.replaceAll("<[^>]*>", "").replace("&", "");

        CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO orbit_logs (log_level, message) VALUES (?, ?);";
            try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, level);
                pstmt.setString(2, cleanMessage);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка записи лога в H2: " + e.getMessage());
            }
        });
    }

    public StatusReport getStatusReport() {
        int successCount = 0;
        int errorCount = 0;
        List<String> recentLogs = new ArrayList<>();

        // Запрос 1: Считаем количество успешных ответов (уровень 2) и ошибок (уровень 1) за последний час
        String statsSql = "SELECT " +
                "COUNT(CASE WHEN log_level = 2 THEN 1 END) as success, " +
                "COUNT(CASE WHEN log_level = 1 THEN 1 END) as errors " +
                "FROM orbit_logs WHERE timestamp >= DATEADD('HOUR', -1, CURRENT_TIMESTAMP());";

        // Запрос 2: Получаем последние 3 записи из лога
        String logsSql = "SELECT timestamp, message FROM orbit_logs ORDER BY id DESC LIMIT 3;";

        try (Connection conn = getConnection()) {
            // Получаем статистику
            try (PreparedStatement pstmt = conn.prepareStatement(statsSql); ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    successCount = rs.getInt("success");
                    errorCount = rs.getInt("errors");
                }
            }
            // Получаем последние логи
            try (PreparedStatement pstmt = conn.prepareStatement(logsSql); ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String time = rs.getTimestamp("timestamp").toString().substring(11, 16); // Формат ЧЧ:ММ
                    String msg = rs.getString("message");
                    recentLogs.add("<gray>[" + time + "]</gray> " + msg);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Не удалось получить статус-репорт из H2: " + e.getMessage());
        }

        return new StatusReport(successCount, errorCount, recentLogs);
    }

    public record StatusReport(int successCount, int errorCount, List<String> recentLogs) {}
}
