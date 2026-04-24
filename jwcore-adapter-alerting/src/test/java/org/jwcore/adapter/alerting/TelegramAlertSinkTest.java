package org.jwcore.adapter.alerting;

import org.jwcore.domain.events.AlertEvent;
import org.jwcore.domain.events.AlertSeverity;
import org.jwcore.domain.events.AlertType;
import org.jwcore.domain.ExecutionState;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramAlertSinkTest {

    @Test
    void shouldBuildUrlAndEncodedBody() {
        final FakeTransport transport = new FakeTransport(List.of(response(200, Optional.empty())));
        final FakeSleeper sleeper = new FakeSleeper();
        final TelegramAlertSink sink = new TelegramAlertSink(new TelegramAlertSinkConfig("token123", "chat id", true),
                transport, new AlertFormatter(), sleeper);

        sink.deliver(alert(AlertSeverity.CRITICAL, ExecutionState.HALT));

        assertTrue(transport.requests.get(0).url().contains("token123"));
        final String decoded = URLDecoder.decode(transport.requests.get(0).body(), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("chat_id=chat id"));
        assertTrue(decoded.contains("ZAMROŻENIE"));
    }

    @Test
    void shouldRetryOnRetriableCodes() {
        final FakeTransport transport = new FakeTransport(List.of(response(503, Optional.empty()), response(500, Optional.empty()), response(200, Optional.empty())));
        final FakeSleeper sleeper = new FakeSleeper();
        final TelegramAlertSink sink = new TelegramAlertSink(new TelegramAlertSinkConfig("t", "c", true), transport, new AlertFormatter(), sleeper);

        sink.deliver(alert(AlertSeverity.WARNING, ExecutionState.SAFE));

        assertEquals(3, transport.requests.size());
        assertEquals(List.of(1000L, 2000L), sleeper.calls);
    }

    @Test
    void shouldUseRetryAfterFor429CappedTo30s() {
        final FakeTransport transport = new FakeTransport(List.of(response(429, Optional.of(60)), response(200, Optional.empty())));
        final FakeSleeper sleeper = new FakeSleeper();
        final TelegramAlertSink sink = new TelegramAlertSink(new TelegramAlertSinkConfig("t", "c", true), transport, new AlertFormatter(), sleeper);

        sink.deliver(alert(AlertSeverity.WARNING, ExecutionState.SAFE));

        assertEquals(List.of(30000L), sleeper.calls);
    }

    @Test
    void shouldNotRetryOn4xx() {
        final FakeTransport transport = new FakeTransport(List.of(response(400, Optional.empty())));
        final TelegramAlertSink sink = new TelegramAlertSink(new TelegramAlertSinkConfig("t", "c", true), transport, new AlertFormatter(), millis -> {});

        sink.deliver(alert(AlertSeverity.WARNING, ExecutionState.SAFE));

        assertEquals(1, transport.requests.size());
    }

    @Test
    void shouldSkipWhenDisabled() {
        final FakeTransport transport = new FakeTransport(List.of(response(200, Optional.empty())));
        final TelegramAlertSink sink = new TelegramAlertSink(TelegramAlertSinkConfig.disabled(), transport, new AlertFormatter(), millis -> {});

        sink.deliver(alert(AlertSeverity.WARNING, ExecutionState.SAFE));

        assertEquals(0, transport.requests.size());
        assertEquals("telegram-disabled", sink.name());
    }

    private static AlertEvent alert(final AlertSeverity severity, final ExecutionState to) {
        return new AlertEvent(UUID.randomUUID(), "acc", severity, ExecutionState.RUN, to, AlertType.STATE_TRANSITION,
                "state-transition", List.of(), Instant.parse("2026-04-24T12:00:00Z"));
    }

    private static TelegramHttpTransport.TelegramResponse response(final int code, final Optional<Integer> retryAfter) {
        return new TelegramHttpTransport.TelegramResponse(code, "body", retryAfter);
    }

    private static final class FakeTransport implements TelegramHttpTransport {
        private final List<TelegramResponse> responses;
        private final List<TelegramRequest> requests = new ArrayList<>();
        private int idx;

        private FakeTransport(final List<TelegramResponse> responses) {
            this.responses = responses;
        }

        @Override
        public CompletableFuture<TelegramResponse> sendAsync(final TelegramRequest request) {
            requests.add(request);
            final TelegramResponse response = idx < responses.size() ? responses.get(idx++) : responses.get(responses.size() - 1);
            return CompletableFuture.completedFuture(response);
        }
    }

    private static final class FakeSleeper implements BackoffSleeper {
        private final List<Long> calls = new ArrayList<>();

        @Override
        public void sleep(final long millis) {
            calls.add(millis);
        }
    }
}
