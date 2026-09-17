package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * What the caller may do with one membership. Decided from the caller's own memberships and the
 * two ids on the membership in hand, so a whole roster costs no queries.
 *
 * <p>{@code canUpdateRole} and {@code canDelete} share the guard {@code canManageMemberships}, which
 * is community-wide rather than per-membership; they are reported separately because they are
 * separate things to offer a user, and because the rule behind them may not stay shared.</p>
 */
@Component
public class MembershipCapabilitiesAssembler {

    private final AccessPolicies policies;

    public MembershipCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public MembershipCapabilitiesResponse assemble(User caller, CommunityMembership membership) {
        UUID communityId = membership.getCommunity() != null ? membership.getCommunity().getId() : null;
        UUID userId = membership.getUser() != null ? membership.getUser().getId() : null;
        boolean canManageMemberships = policies.membership()
                .canManageMemberships(caller, communityId).isAllowed();

        return MembershipCapabilitiesResponse.builder()
                .withCanUpdateRole(canManageMemberships)
                .withCanDelete(canManageMemberships)
                .withCanManageInvestment(policies.membership().canManageInvestment(caller, communityId).isAllowed())
                .withCanReadPayback(policies.membership().canReadPayback(caller, communityId, userId).isAllowed())
                .build();
    }
}
