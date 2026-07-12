package org.cosmIk.orbitkey;

import org.cosmIk.orbitkey.commands.OrbitCommand;
import org.cosmIk.orbitkey.config.ConfigManager;
import org.cosmIk.orbitkey.http.WebClient;
import org.cosmIk.orbitkey.db.DatabaseLogger;
import org.cosmIk.orbitkey.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class OrbitKey extends JavaPlugin {

    private ConfigManager configManager;
    private WebClient webClient;
    private BukkitTask pingTask;
    private DatabaseLogger databaseLogger;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.databaseLogger = new DatabaseLogger(this);

        this.webClient = new WebClient(this);

        // Регистрация команд
        OrbitCommand cmd = new OrbitCommand(this);
        if (getCommand("orbit") != null) {
            getCommand("orbit").setExecutor(cmd);
            getCommand("orbit").setTabCompleter(cmd);
        }

        startPingScheduler();

        MessageUtils.sendToOpsAndConsole(configManager.getDebugMode(), 0, configManager.getMessage("messages.enabled", "&aПлагин OrbitKey успешно запущен."));
    }

    @Override
    public void onDisable() {
        if (pingTask != null) pingTask.cancel();
        // Отправляем синхронный запрос закрытия, пока поток основного процесса Bukkit еще жив
        if (webClient != null) {
            webClient.sendRequest("shutdown", false);
        }
        MessageUtils.sendToOpsAndConsole(configManager.getDebugMode(), 0, configManager.getMessage("messages.disabled", "&cПлагин OrbitKey выключен."));
    }

    public void startPingScheduler() {
        if (pingTask != null) pingTask.cancel();
        // Запуск асинхронной цикличной проверки каждые 30 сек (600 тиков)
        this.pingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
                () -> webClient.sendRequest("ping", true), 100L, 600L);
    }

    public void reloadPluginData() {
        this.configManager.load();
        startPingScheduler(); // Перезапускаем таймер с новыми параметрами URL, если они поменялись
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseLogger getDatabaseLogger() { return databaseLogger; }
    public WebClient getWebClient() { return webClient; }
}
