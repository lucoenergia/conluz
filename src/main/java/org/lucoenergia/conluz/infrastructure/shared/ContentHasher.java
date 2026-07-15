package org.lucoenergia.conluz.infrastructure.shared;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes a stable, hex-encoded SHA-256 digest of file content, used to detect re-uploads of the
 * same distributor file.
 */
public final class ContentHasher {

    private ContentHasher() {
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
