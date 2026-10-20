package org.lucoenergia.conluz.domain.admin.user.platformadmin;


import java.time.Instant;
import java.util.UUID;

/**
 * A pending invitation to the platform-admin role, as the list shows it: only what the inviter typed and the state
 * of the invitation, never anything from the account, so that the row is the same whether or not the account
 * existed (ADR-0004 §2).
 *
 * @param userId        the invited account
 * @param fullName      the full name typed by the inviter
 * @param email         the address the invitation was sent to
 * @param invitedAt     when the invitation was created
 * @param linkExpiresAt when the current link stops being valid, or null when there is no valid link: it expired, or
 *                      it was revoked
 */
public record PendingPlatformAdmin(UUID userId, String fullName, String email, Instant invitedAt,
                                   Instant linkExpiresAt) implements PlatformAdminListEntry {
}
