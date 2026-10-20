package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The stored form of a confirmation code: the lowercase hex SHA-256 hash of the invitation id and the normalised
 * code. The invitation id makes the same code hash differently on two invitations.
 */
final class ConfirmationCodeHash {

    private ConfirmationCodeHash() {
    }

    static String of(UUID invitationId, ConfirmationCode code) {
        return hash(invitationId, code.value());
    }

    /**
     * Compares in constant time, so that how long the answer takes says nothing about how much of the code is
     * right.
     *
     * @return false if nothing was typed
     */
    static boolean matches(UUID invitationId, String typed, String storedHash) {
        String normalized = ConfirmationCode.normalize(typed);
        if (normalized == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(invitationId, normalized).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hash(UUID invitationId, String normalizedCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((invitationId + ":" + normalizedCode).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
