package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.MembershipCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

import java.util.UUID;

@Schema(requiredProperties = {"id", "user", "communityId", "role", "enabled", "capabilities"})
public class MembershipResponse {

    private final UUID id;
    @Schema(description = "User associated with the membership", types = {"object", "null"})
    private final UserResponse user;
    private final UUID communityId;
    private final CommunityRole role;
    private final Boolean enabled;

    @Schema(description = "What the caller may do with this membership.")
    private final MembershipCapabilitiesResponse capabilities;

    public MembershipResponse(CommunityMembership membership, UserCapabilitiesResponse userCapabilities,
                              MembershipCapabilitiesResponse capabilities) {
        this.id = membership.getId();
        this.user = membership.getUser() != null
                ? new UserResponse(membership.getUser(), userCapabilities)
                : null;
        this.communityId = membership.getCommunity().getId();
        this.role = membership.getRole();
        this.enabled = membership.isEnabled();
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

    public MembershipCapabilitiesResponse getCapabilities() {
        return capabilities;
    }
}
