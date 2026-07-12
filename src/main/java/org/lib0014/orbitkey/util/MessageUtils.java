package org.lib0014.orbitkey.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Парсит строку, автоматически конвертируя старые коды & в формат MiniMessage,
     * после чего преобразует её в валидный Component.
     */
    public static Component parse(String message) {
        if (message == null) return Component.empty();
        // Преобразуем старые &a, &c в MiniMessage-совместимый компонент, если они есть
        if (message.contains("&")) {
            return LEGACY_SERIALIZER.deserialize(message);
        }
        return MINI_MESSAGE.deserialize(message);
    }

    public static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(parse(message));
    }

    public static void sendToOpsAndConsole(int debugMode, int requiredLevel, String message) {
        if (debugMode < requiredLevel) return;

        Component prefix = parse("<gradient:#12c2e9:#c471ed:#f64f59>[OrbitKey]</gradient> ");
        Component content = parse(message);
        Component finalMessage = prefix.append(content);

        // Отправка в консоль
        Bukkit.getConsoleSender().sendMessage(finalMessage);

        // Отправка операторам онлайн
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                player.sendMessage(finalMessage);
            }
        }
    }
}
