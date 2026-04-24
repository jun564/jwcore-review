package org.jwcore.adapter.alerting;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface TelegramHttpTransport {
    CompletableFuture<TelegramResponse> sendAsync(TelegramRequest request);

    record TelegramRequest(String url, String body, int timeoutSeconds) {}
    record TelegramResponse(int statusCode, String body, Optional<Integer> retryAfterSeconds) {}
}
