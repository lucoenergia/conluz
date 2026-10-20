package org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;

/**
 * A platform-admin invitation just sent: its row as the list shows it, and the confirmation code generated for it,
 * which is shown this once and never again (ADR-0004 §4).
 */
public record InvitedPlatformAdmin(PendingPlatformAdmin platformAdmin, ConfirmationCode confirmationCode) {
}
