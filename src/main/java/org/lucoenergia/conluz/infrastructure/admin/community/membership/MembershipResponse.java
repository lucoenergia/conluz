package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "user", "communityId", "role", "enabled", "acceptedAt", "invitationExpiresAt", "capabilities"})
public class MembershipResponse {

    private final UUID id;
    @Schema(description = "User associated with the membership", types = {"object", "null"})
    private final UserResponse user;
    private final UUID communityId;
    private final CommunityRole role;
    private final Boolean enabled;
    @Schema(description = "When the membership was accepted. Null while it is pending: a pending membership "
            + "grants no access until it is accepted. Independent of enabled.",
            example = "2026-01-01T00:00:00Z", types = {"string", "null"})
    private final Instant acceptedAt;
    @Schema(description = "When the membership's invitation link expires. Null when it has no link that can still "
            + "be used: none was sent, it was revoked or used, or it has expired.",
            example = "2026-01-08T00:00:00Z", types = {"string", "null"})
    private final Instant invitationExpiresAt;

    @Schema(description = "What the caller may do with this membership.")
    private final MembershipCapabilitiesResponse capabilities;

    /**
     * @param invitationExpiresAt when the membership's invitation link expires, null when it has none
     */
    public MembershipResponse(CommunityMembership membership, Instant invitationExpiresAt,
                              UserCapabilitiesResponse userCapabilities, MembershipCapabilitiesResponse capabilities) {
        this.id = membership.getId();
        this.user = membership.getUser() != null
                ? new UserResponse(membership.getUser(), userCapabilities)
                : null;
        this.communityId = membership.getCommunity().getId();
        this.role = membership.getRole();
        this.enabled = membership.isEnabled();
        this.acceptedAt = membership.getAcceptedAt();
        this.invitationExpiresAt = invitationExpiresAt;
        this.capabilities = capabilities;
    }

    public UUID getId() {
        return id;
    }

    public UserResponse getUser() {
        return user;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public CommunityRole getRole() {
        return role;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getInvitationExpiresAt() {
        return invitationExpiresAt;
    }

    public MembershipCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
