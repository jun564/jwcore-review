package org.jwcore.adapter.alerting;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class HttpTelegramHttpTransport implements TelegramHttpTransport {
    private final HttpClient httpClient;

    public HttpTelegramHttpTransport() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public CompletableFuture<TelegramResponse> sendAsync(final TelegramRequest request) {
        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(java.net.URI.create(request.url()))
                .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(request.body()))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    final Optional<Integer> retryAfter = response.headers().firstValue("Retry-After")
                            .flatMap(v -> {
                                try {
                                    return Optional.of(Integer.parseInt(v));
                                } catch (NumberFormatException exception) {
                                    return Optional.empty();
                                }
                            });
                    return new TelegramResponse(response.statusCode(), response.body(), retryAfter);
                })
                .exceptionally(throwable -> {
                    throw new RuntimeException(new IOException("Telegram HTTP call failed", throwable));
                });
    }
}
