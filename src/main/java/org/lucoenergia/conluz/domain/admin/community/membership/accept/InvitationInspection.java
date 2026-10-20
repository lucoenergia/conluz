package org.lucoenergia.conluz.domain.admin.community.membership.accept;

import java.util.UUID;

/**
 * What the holder of an invitation link learns before accepting it: what the invitation is for, the community when
 * it is for one, whether the account must log in to accept it, because it already has a password, and whether a
 * confirmation code is required, as it is for the platform-admin role (ADR-0004 §5).
 *
 * @param communityId   the community, or null for a platform-admin invitation
 * @param communityName the community's name, or null for a platform-admin invitation
 */
public record InvitationInspection(InvitationKind kind, UUID communityId, String communityName, boolean loginRequired,
                                   boolean confirmationCodeRequired) {

    public InvitationInspection(UUID communityId, String communityName, boolean loginRequired) {
        this(InvitationKind.COMMUNITY, communityId, communityName, loginRequired, false);
    }

    public static InvitationInspection platform(boolean loginRequired) {
        return new InvitationInspection(InvitationKind.PLATFORM, null, null, loginRequired, true);
    }
}
