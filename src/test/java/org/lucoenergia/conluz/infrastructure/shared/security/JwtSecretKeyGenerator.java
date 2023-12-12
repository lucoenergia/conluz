package org.lucoenergia.conluz.infrastructure.shared.security;

import java.security.SecureRandom;

public class JwtSecretKeyGenerator {
    public static String generate() {
        // Generate a random 256-bit (32-byte) key for HMAC-SHA-256
        byte[] secretKey = generateRandomKey(32);

        return bytesToHex(secretKey);
    }

    /**
     * Generate a random key of {@code length} bytes
     */
    private static byte[] generateRandomKey(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[length];
        secureRandom.nextBytes(key);
        return key;
    }

    /**
     * Converts the byte array to a hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
