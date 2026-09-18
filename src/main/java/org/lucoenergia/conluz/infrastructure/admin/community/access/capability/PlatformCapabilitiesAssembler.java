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
        // Three of these four lines evaluate to the same predicate today -- each reduces to "is the
        // caller a platform admin" -- and no test can tell them apart while they do. They are three
        // different questions: may I open the administration surface, may I create a community, may
        // I create a user belonging to no community. They are kept apart so any one of them can
        // diverge without a call site changing, so do not fold them into a shared local or a single
        // field. See the platform section of docs/security/capability-inventory.md.
        return PlatformCapabilitiesResponse.builder()
                .withCanCreateCommunity(policies.platform().canAdministerPlatform(caller).isAllowed())
                .withCanListUsers(policies.user().canList(caller).isAllowed())
                .withCanAdministerPlatform(policies.platform().canAdministerPlatform(caller).isAllowed())
                // The null is the rule's own no-community case, not a missing argument:
                // authorization-policy.md records that a platform admin may create a user attached
                // to no community, which is what POST /api/v1/users does without a communityId.
                .withCanCreateUsers(policies.user().canCreateIn(caller, null).isAllowed())
                .build();
    }
}
