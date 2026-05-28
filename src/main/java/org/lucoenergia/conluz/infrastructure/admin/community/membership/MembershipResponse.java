package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;

import java.util.UUID;

public class MembershipResponse {

    private final UUID id;
    private final UUID userId;
    private final UUID communityId;
    private final CommunityRole role;
    private final Boolean enabled;

    public MembershipResponse(CommunityMembership membership) {
        this.id = membership.getId();
        this.userId = membership.getUser().getId();
        this.communityId = membership.getCommunity().getId();
        this.role = membership.getRole();
        this.enabled = membership.isEnabled();
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
}
