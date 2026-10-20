package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * The confirmation code of a platform-admin invitation (ADR-0004 §4): 8 characters drawn from a cryptographic random
 * source, from the Crockford base32 alphabet, which leaves out I, L, O and U so that it can be read out and typed
 * without ambiguity. The inviter sees it once, in the response that generated it, and passes it to the person by
 * another channel. It is never emailed, logged or stored: {@link #toString()} does not show it.
 */
public record ConfirmationCode(String value) {

    public static final int LENGTH = 8;
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static ConfirmationCode generate() {
        byte[] bytes = new byte[LENGTH];
        RANDOM.nextBytes(bytes);
        StringBuilder code = new StringBuilder(LENGTH);
        for (byte b : bytes) {
            // 32 symbols divide 256 exactly, so masking keeps every symbol equally likely
            code.append(ALPHABET.charAt(b & (ALPHABET.length() - 1)));
        }
        return new ConfirmationCode(code.toString());
    }

    /**
     * The code as typed, in the form it was generated: spaces and hyphens removed, upper case, and the letters
     * Crockford base32 reads as digits (O as 0, I and L as 1) replaced by them.
     *
     * @return the normalised code, or null if nothing was typed
     */
    public static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String normalized = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT)
                .replace('O', '0').replace('I', '1').replace('L', '1');
        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    public String toString() {
        return "ConfirmationCode[redacted]";
    }
}
