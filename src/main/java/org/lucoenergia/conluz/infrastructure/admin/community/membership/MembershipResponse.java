package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.UUID;

public class MembershipResponse {

    private final UUID id;
    private final UUID userId;
    private final UUID communityId;
    private final CommunityRole role;
    private final Boolean enabled;

    @Schema(description = "Full name of the member")
    private final String fullName;

    @Schema(description = "Email of the member")
    private final String email;

    public MembershipResponse(CommunityMembership membership) {
        this.id = membership.getId();
        this.userId = membership.getUser() != null ? membership.getUser().getId() : null;
        this.communityId = membership.getCommunity().getId();
        this.role = membership.getRole();
        this.enabled = membership.isEnabled();
        this.fullName = membership.getUser() != null ? membership.getUser().getFullName() : null;
        this.email = membership.getUser() != null ? membership.getUser().getEmail() : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
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

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }
}
