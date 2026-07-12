package org.lib0014.orbitkey.commands;

import org.lib0014.orbitkey.OrbitKey;
import org.lib0014.orbitkey.db.DatabaseLogger;
import org.lib0014.orbitkey.util.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public class OrbitCommand implements CommandExecutor, TabCompleter {
    private final OrbitKey plugin;

    public OrbitCommand(OrbitKey plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var cfg = plugin.getConfigManager();

        if (args.length >= 2 && args[0].equalsIgnoreCase("key")) {
            String sub = args[1].toLowerCase();

            switch (sub) {
                case "info" -> {
                    if (hasPerm(sender, "orbit.key.info")) {
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.info.title", "&b--- [ OrbitKey Info ] ---"));
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.info.version", "&7Версия: &f1.0.0"));
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.info.author", "&7Автор: &fcosmIk"));
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.info.links", "&7Ссылки: &9https://github.com"));
                    } else sendNoPerm(sender);
                    return true;
                }
                case "site" -> {
                    if (hasPerm(sender, "orbit.key.site")) {
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.site-url", "&aСсылка на сайт: &f") + cfg.getUrl());
                    } else sendNoPerm(sender);
                    return true;
                }
                case "reload" -> {
                    if (hasPerm(sender, "orbit.key.reload")) {
                        plugin.reloadPluginData();
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.reload-success", "&aКонфигурация и локализация успешно перезагружены!"));
                    } else sendNoPerm(sender);
                    return true;
                }
                case "status" -> {
                    if (hasPerm(sender, "orbit.key.status")) {
                        // Запрашиваем данные из H2
                        DatabaseLogger.StatusReport report = plugin.getDatabaseLogger().getStatusReport();

                        // Формируем красивый статус (онлайн/оффлайн) на основе наличия ошибок
                        String statusIndicator = report.errorCount() > 0 && report.successCount() == 0
                                ? "<red><b>● ОШИБКА СВЯЗИ</b></red>"
                                : "<green><b>● СТАБИЛЬНО</b></green>";

                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.status.header", "<dark_aqua>==== [ Состояние OrbitKey ] ====</dark_aqua>"));
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.status.connection", "<gray>Статус сети:</gray> ") + statusIndicator);
                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.status.stats", "<gray>За последний час:</gray> <green>Успешно: %success%</green> | <red>Ошибок: %errors%</red>")
                                .replace("%success%", String.valueOf(report.successCount()))
                                .replace("%errors%", String.valueOf(report.errorCount())));

                        MessageUtils.sendMessage(sender, cfg.getMessage("messages.status.logs-header", "<gray>Последние события журнала:</gray>"));
                        if (report.recentLogs().isEmpty()) {
                            MessageUtils.sendMessage(sender, " <italic><gray>Журнал пуст...</gray></italic>");
                        } else {
                            for (String logLine : report.recentLogs()) {
                                MessageUtils.sendMessage(sender, " " + logLine);
                            }
                        }
                    } else sendNoPerm(sender);
                    return true;
                }
            }
        }

        MessageUtils.sendMessage(sender, "&cИспользуйте: /orbit key [info/site/reload/status]");
        return true;
    }

    private boolean hasPerm(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission("orbit.key.use") || sender.isOp();
    }

    private void sendNoPerm(CommandSender sender) {
        MessageUtils.sendMessage(sender, plugin.getConfigManager().getMessage("messages.no-permission", "&cУ вас нет прав!"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("key");
        if (args.length == 2 && args[0].equalsIgnoreCase("key")) {
            return Arrays.asList("info", "site", "reload", "status");
        }
        return List.of();
    }
}
