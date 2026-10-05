package org.lucoenergia.conluz.domain.admin.user.auth;

import java.util.UUID;

/**
 * The claims of a token whose signature, format and expiry have been verified.
 *
 * @param userId the token's subject
 * @param jti    the token's id, by which it can be revoked
 */
public record VerifiedToken(UUID userId, String jti) {
}
