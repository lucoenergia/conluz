package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;

import java.util.UUID;

public class MembershipResponse {

    private final UUID id;
    @Schema(description = "User associated with the membership")
    private final UserResponse user;
    private final UUID communityId;
    private final CommunityRole role;
    private final Boolean enabled;

    public MembershipResponse(CommunityMembership membership) {
        this.id = membership.getId();
        this.user = membership.getUser() != null ? new UserResponse(membership.getUser()) : null;
        this.communityId = membership.getCommunity().getId();
        this.role = membership.getRole();
        this.enabled = membership.isEnabled();
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
}
