package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

/**
 * The platform-wide capabilities of the caller, for {@code GET /api/v1/users/current}.
 *
 * <p>Like every assembler here it reads the same policies the guards read, so a capability and the
 * endpoint it describes cannot disagree. It holds no rules of its own, loads nothing, and throws
 * nothing — a denial is simply {@code false}.</p>
 *
 * <p>{@code caller} is never null: every endpoint that returns capabilities is authenticated, so
 * {@code @AuthenticationPrincipal} has already produced a user by the time a response is built.</p>
 */
@Component
public class PlatformCapabilitiesAssembler {

    private final AccessPolicies policies;

    public PlatformCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public PlatformCapabilitiesResponse assemble(User caller) {
        return PlatformCapabilitiesResponse.builder()
                .withCanCreateCommunity(policies.platform().canAdministerPlatform(caller).isAllowed())
                .withCanListUsers(policies.user().canList(caller).isAllowed())
                .build();
    }
}
