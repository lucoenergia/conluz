package org.lucoenergia.conluz.domain.admin.user.token;

import org.lucoenergia.conluz.domain.shared.UserId;

import java.util.UUID;

/**
 * The platform-admin invitation a token was issued for, and the account it invites.
 */
public record OneTimeTokenPlatformAdminInvitation(UUID invitationId, UserId userId) {
}
