package org.lib0014.orbitkey.config;

import org.lib0014.orbitkey.OrbitKey;
import org.lib0014.orbitkey.util.MessageUtils;
import org.lib0014.orbitkey.util.TokenGenerateUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ConfigManager {
    private final OrbitKey plugin;
    private FileConfiguration langConfig;

    private String url;
    private String token;
    private int debugMode;
    private int loggerMode;
    private String loggerType;

    public ConfigManager(OrbitKey plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.url = config.getString("url", "https://server.com");
        this.debugMode = config.getInt("debug-mode", 1);
        this.loggerMode = config.getInt("logger-mode", 1);
        this.loggerType = config.getString("logger-type", "H2");

        // Автогенерация 64-значного токена безопасности
        if (config.getString("token", "").isBlank()) {
            this.token = TokenGenerateUtils.generateSecureToken(64);
            config.set("token", this.token);
            plugin.saveConfig();
            MessageUtils.sendToOpsAndConsole(debugMode, 1, "&eСгенерирован новый безопасный токен: &f" + this.token);
        } else {
            this.token = config.getString("token");
        }

        loadLanguage(config.getString("language", "ru_ru"));
        generateWebTemplate(); // Создаем скрипт для сайта
    }

    private void loadLanguage(String langName) {
        File langFile = new File(plugin.getDataFolder() + File.separator + "language", langName + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("language/" + langName + ".yml", false);
        }
        this.langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    private void generateWebTemplate() {
        File webFile = new File(plugin.getDataFolder(), "web_template.php");
        if (webFile.exists()) return;

        String phpTemplate = """
        <?php
        // Шаблон защищенного скрипта для OrbitKey (Purpur API)
        $secret_token = "%s"; 
        $cache_file = __DIR__ . '/server_data.json';

        // Валидация подписи (Защита от подделки данных и Replay-атак)
        $timestamp = $_GET['time'] ?? 0;
        $received_signature = $_GET['sign'] ?? '';
        
        // Защита: Запрос устарел более чем на 30 секунд (Replay Attack Protection)
        if (abs(time() - $timestamp) > 30) {
            http_response_code(403);
            die("Error: Request Expired");
        }

        // Вычисляем эталонный HMAC-SHA256 хэш
        $action = $_GET['action'] ?? '';
        $online = $_GET['online'] ?? 0;
        $max = $_GET['max'] ?? 0;
        $status = $_GET['status'] ?? '';
        
        $data_string = "action=$action&time=$timestamp&online=$online&max=$max&status=$status";
        $expected_signature = hash_hmac('sha256', $data_string, $secret_token);

        if (!hash_equals($expected_signature, $received_signature)) {
            http_response_code(401);
            die("Error: Invalid Signature");
        }

        // Обработка данных
        if ($action === 'ping' || $action === 'shutdown') {
            $data = [
                'status' => $status,
                'online' => (int)$online,
                'max' => (int)$max,
                'last_ping' => time()
            ];
            file_put_contents($cache_file, json_encode($data));
            echo "OK";
            exit;
        }
        ?>
        """.formatted(this.token);

        try {
            Files.writeString(webFile.toPath(), phpTemplate);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сгенерировать веб-шаблон: " + e.getMessage());
        }
    }

    public String getMessage(String path, String def) {
        if (langConfig == null) return def;
        return langConfig.getString(path, def);
    }

    // Геттеры
    public String getUrl() { return url; }
    public String getToken() { return token; }
    public int getDebugMode() { return debugMode; }
    public int getLoggerMode() { return loggerMode; }
    public String getLoggerType() { return loggerType; }
}
