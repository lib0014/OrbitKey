package org.lib0014.orbitkey.http;

import org.lib0014.orbitkey.OrbitKey;
import org.lib0014.orbitkey.util.MessageUtils;
import org.bukkit.Bukkit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

public class WebClient {
    private final OrbitKey plugin;
    private final HttpClient httpClient;

    public WebClient(OrbitKey plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Отправляет пакет данных на веб-сайт.
     *
     * @param action Действие (например, "ping" или "shutdown")
     * @param async  Флаг асинхронного выполнения (true для планировщика, false для onDisable)
     */
    public void sendRequest(String action, boolean async) {
        var cfg = plugin.getConfigManager();
        if (!cfg.getUrl().startsWith("http")) return;

        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            int online = Bukkit.getOnlinePlayers().size();
            int max = Bukkit.getMaxPlayers();
            String status = action.equals("shutdown") ? "offline" : "online";

            // Сборка строки параметров для генерации цифровой подписи HMAC
            String dataToSign = String.format("action=%s&time=%d&online=%d&max=%d&status=%s",
                    action, timestamp, online, max, status);

            String signature = calculateHMAC(dataToSign, cfg.getToken());

            // Формирование защищенного URL с подписью и меткой времени (защита от Replay-атак)
            String query = String.format("?action=%s&time=%d&online=%d&max=%d&status=%s&sign=%s",
                    action, timestamp, online, max, status, URLEncoder.encode(signature, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getUrl() + query))
                    .GET()
                    .build();

            String debugMsg = "<gray>Отправка сетевого пакета данных... [Действие: " + action + ", Асинхронно: " + async + "]</gray>";
            MessageUtils.sendToOpsAndConsole(cfg.getDebugMode(), 2, debugMsg);
            plugin.getDatabaseLogger().logAsync(2, debugMsg);

            if (async) {
                // Асинхронный HTTP-запрос (не блокирует основной поток игрового сервера)
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(res -> handleResponse(res.statusCode()))
                        .exceptionally(ex -> {
                            String errorMsg = "<red>Ошибка отправки асинхронного пакета на сайт: " + ex.getMessage() + "</red>";
                            MessageUtils.sendToOpsAndConsole(cfg.getDebugMode(), 1, errorMsg);
                            plugin.getDatabaseLogger().logAsync(1, errorMsg);
                            return null;
                        });
            } else {
                // Блокирующий синхронный вызов (применяется строго при завершении работы сервера)
                var res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                handleResponse(res.statusCode());
            }

        } catch (Exception e) {
            String critMsg = "<dark_red><b>Критический сбой криптографии или отправки:</b> " + e.getMessage() + "</dark_red>";
            MessageUtils.sendToOpsAndConsole(cfg.getDebugMode(), 1, critMsg);
            plugin.getDatabaseLogger().logAsync(1, critMsg);
        }
    }

    /**
     * Обрабатывает статус-код ответа веб-сервера, выводит логи операторам и записывает их в H2.
     */
    private void handleResponse(int code) {
        var cfg = plugin.getConfigManager();

        if (code == 200) {
            String msg = "<green>Связь успешно подтверждена! Веб-скрипт верифицировал подпись пакета.</green>";
            MessageUtils.sendToOpsAndConsole(cfg.getDebugMode(), 2, msg);
            plugin.getDatabaseLogger().logAsync(2, msg);
        } else if (code == 401 || code == 403) {
            String msg = "<red><b>Запрос отклонен веб-сервером!</b> Код: " + code + " (Ошибка токена, времени или подписи HMAC)</red>";
            MessageUtils.sendToOpsAndConsole(cfg.getDebugMode(), 1, msg);
            plugin.getDatabaseLogger().logAsync(1, msg);
        } else {
            String msg = "<yellow>Скрипт сайта вернул неожиданный HTTP-код ответа: " + code + "</yellow>";
            MessageUtils.sendToOpsAndConsole(cfg.getDebugMode(), 1, msg);
            plugin.getDatabaseLogger().logAsync(1, msg);
        }
    }

    /**
     * Вычисляет криптографическую подпись HMAC-SHA256 для защиты целостности данных.
     */
    private String calculateHMAC(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder(rawHmac.length * 2);
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
