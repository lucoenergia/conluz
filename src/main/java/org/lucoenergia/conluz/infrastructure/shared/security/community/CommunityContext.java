package org.lucoenergia.conluz.infrastructure.shared.security.community;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;
import java.util.UUID;

@Component
@RequestScope
public class CommunityContext {

    private UUID activeCommunityId;

    public Optional<UUID> getActiveCommunityId() {
        return Optional.ofNullable(activeCommunityId);
    }

    public void setActiveCommunityId(UUID communityId) {
        this.activeCommunityId = communityId;
    }

    public boolean isSet() {
        return activeCommunityId != null;
    }
}
