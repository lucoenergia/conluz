package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * What the caller may do with a community. Every rule here is decided from the caller's memberships
 * alone, so this costs no queries however many communities a listing returns.
 *
 * <p>Two capabilities share a rule with a third. {@code canReadProduction} and
 * {@code canListSupplies} are both {@code CommunityAccessPolicy.isMember}, which is what the guards
 * {@code canReadCommunityProduction} and {@code canListSupplies} delegate to; they are reported
 * separately because reading production and listing supplies are different things to be allowed to
 * do, however alike the rule behind them is today.</p>
 */
@Component
public class CommunityCapabilitiesAssembler {

    private final AccessPolicies policies;

    public CommunityCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public CommunityCapabilitiesResponse assemble(User caller, UUID communityId) {
        boolean administersPlatform = policies.platform().canAdministerPlatform(caller).isAllowed();
        boolean isMember = policies.community().isMember(caller, communityId).isAllowed();

        return CommunityCapabilitiesResponse.builder()
                .withCanRead(policies.community().canRead(caller, communityId).isAllowed())
                .withCanUpdate(administersPlatform)
                .withCanEnable(administersPlatform)
                .withCanDisable(administersPlatform)
                .withCanManage(policies.community().canManage(caller, communityId).isAllowed())
                .withCanManageMemberships(policies.membership().canManageMemberships(caller, communityId).isAllowed())
                .withCanManageMembershipInvestment(
                        policies.membership().canManageInvestment(caller, communityId).isAllowed())
                .withCanListPlants(policies.plant().canList(caller, communityId).isAllowed())
                .withCanCreatePlants(policies.plant().canCreateIn(caller, communityId).isAllowed())
                .withCanCreateUsers(policies.user().canCreateIn(caller, communityId).isAllowed())
                .withCanReadProduction(isMember)
                .withCanListSupplies(isMember)
                .build();
    }
}
