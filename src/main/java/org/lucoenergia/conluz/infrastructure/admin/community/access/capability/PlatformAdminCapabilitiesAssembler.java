package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * What the caller may do with one platform admin of {@code GET /api/v1/platform-admins}. Decided from
 * the caller and the id on the row alone, so the whole list costs no queries.
 *
 * <p>{@code canRevoke} reads the same policy as the guard of {@code revoke-platform-admin}, and
 * nothing else: the last-platform-admin rule is state, reported on the row as {@code revocable}.</p>
 */
@Component
public class PlatformAdminCapabilitiesAssembler {

    private final AccessPolicies policies;

    public PlatformAdminCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public PlatformAdminCapabilitiesResponse assemble(User caller, UUID userId) {
        return PlatformAdminCapabilitiesResponse.builder()
                .withCanRevoke(policies.platform().canAdministerOtherUser(caller, userId).isAllowed())
                .build();
    }
}
