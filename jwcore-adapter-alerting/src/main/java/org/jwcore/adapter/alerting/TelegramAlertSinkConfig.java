package org.jwcore.adapter.alerting;

import java.util.Objects;

public record TelegramAlertSinkConfig(String botToken, String chatId, boolean enabled) {
    public TelegramAlertSinkConfig {
        if (enabled) {
            Objects.requireNonNull(botToken, "botToken required when enabled");
            Objects.requireNonNull(chatId, "chatId required when enabled");
        }
    }

    public static TelegramAlertSinkConfig fromEnvironment() {
        final String token = System.getenv("JWCORE_TELEGRAM_BOT_TOKEN");
        final String chatId = System.getenv("JWCORE_TELEGRAM_CHAT_ID");
        final boolean enabled = token != null && !token.isBlank() && chatId != null && !chatId.isBlank();
        return new TelegramAlertSinkConfig(enabled ? token : "", enabled ? chatId : "", enabled);
    }

    public static TelegramAlertSinkConfig disabled() {
        return new TelegramAlertSinkConfig("", "", false);
    }
}
