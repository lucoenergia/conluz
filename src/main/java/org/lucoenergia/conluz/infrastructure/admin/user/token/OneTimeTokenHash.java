package org.lucoenergia.conluz.infrastructure.admin.user.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The lowercase hex SHA-256 hash of a one-time token, the only form in which it is stored and looked up.
 */
final class OneTimeTokenHash {

    private OneTimeTokenHash() {
    }

    static String of(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
