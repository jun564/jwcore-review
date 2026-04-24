package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TelegramAlertSink implements AlertSink {
    private static final Logger LOGGER = Logger.getLogger(TelegramAlertSink.class.getName());
    private static final Set<Integer> RETRY_CODES = Set.of(429, 500, 502, 503, 504);
    private static final Set<Integer> NON_RETRY_CODES = Set.of(400, 401, 403, 404);

    private final TelegramAlertSinkConfig config;
    private final TelegramHttpTransport transport;
    private final AlertFormatter formatter;
    private final BackoffSleeper sleeper;

    public TelegramAlertSink(final TelegramAlertSinkConfig config,
                             final TelegramHttpTransport transport,
                             final AlertFormatter formatter,
                             final BackoffSleeper sleeper) {
        this.config = config;
        this.transport = transport;
        this.formatter = formatter;
        this.sleeper = sleeper;
    }

    @Override
    public void deliver(final AlertEvent alert) {
        if (!config.enabled()) {
            LOGGER.warning("Telegram sink is disabled. Alert ignored.");
            return;
        }
        final String url = "https://api.telegram.org/bot" + config.botToken() + "/sendMessage";
        final String message = formatter.formatTelegramMessage(alert);
        final String body = "chat_id=" + URLEncoder.encode(config.chatId(), StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                final TelegramHttpTransport.TelegramResponse response = transport
                        .sendAsync(new TelegramHttpTransport.TelegramRequest(url, body, 10))
                        .get();
                if (response.statusCode() == 200) {
                    return;
                }
                if (NON_RETRY_CODES.contains(response.statusCode())) {
                    LOGGER.severe("Telegram non-retriable status=" + response.statusCode() + " body=" + response.body());
                    return;
                }
                if (RETRY_CODES.contains(response.statusCode())) {
                    if (attempt == 3) {
                        LOGGER.severe("Telegram failed after retries status=" + response.statusCode());
                        return;
                    }
                    sleepForAttempt(attempt, response.retryAfterSeconds().orElse(null), response.statusCode() == 429);
                } else {
                    LOGGER.severe("Telegram unexpected status=" + response.statusCode() + " body=" + response.body());
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Telegram sleep interrupted", exception);
                return;
            } catch (ExecutionException | RuntimeException exception) {
                if (attempt == 3) {
                    LOGGER.log(Level.SEVERE, "Telegram failed after retries", exception);
                    return;
                }
                try {
                    sleeper.sleep(backoffMillis(attempt));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Telegram sleep interrupted", interruptedException);
                    return;
                }
            }
        }
    }

    private void sleepForAttempt(final int attempt, final Integer retryAfterSeconds, final boolean isTooManyRequests) throws InterruptedException {
        if (isTooManyRequests && retryAfterSeconds != null) {
            sleeper.sleep(Math.min(retryAfterSeconds, 30) * 1000L);
            return;
        }
        sleeper.sleep(backoffMillis(attempt));
    }

    private long backoffMillis(final int attempt) {
        return (1L << (attempt - 1)) * 1000L;
    }

    @Override
    public String name() {
        return config.enabled() ? "telegram" : "telegram-disabled";
    }
}
