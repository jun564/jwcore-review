package org.jwcore.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class IdempotencyKeys {

    private static final HexFormat HEX = HexFormat.of();

    private IdempotencyKeys() {
    }

    public static String generate(
            final String brokerOrderId,
            final EventType eventType,
            final byte[] payload) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateWithNullableString(digest, brokerOrderId);
            digest.update((byte) 0x1F);
            updateWithNullableString(digest, eventType.name());
            digest.update((byte) 0x1F);
            if (payload != null) {
                digest.update(payload);
            }
            return HEX.formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm should be available in Java 21", exception);
        }
    }

    private static void updateWithNullableString(final MessageDigest digest, final String value) {
        if (value == null) {
            digest.update((byte) 0x00);
            return;
        }
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
}
