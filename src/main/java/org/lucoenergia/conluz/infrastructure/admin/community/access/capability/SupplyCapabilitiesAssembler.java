package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

/**
 * What the caller may do with a supply, decided over the supply already in hand.
 *
 * <p>{@code canCreatePlant} is the one place this differs mechanically from its guard: the guard
 * takes a CUPS and looks the supply up, because that is what the request body carries. Here the
 * supply is already loaded, so the same rule is asked directly and no query is issued.</p>
 */
@Component
public class SupplyCapabilitiesAssembler {

    private final AccessPolicies policies;

    public SupplyCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public SupplyCapabilitiesResponse assemble(User caller, Supply supply) {
        return SupplyCapabilitiesResponse.builder()
                .withCanRead(policies.supply().canRead(caller, supply).isAllowed())
                .withCanEdit(policies.supply().canEdit(caller, supply).isAllowed())
                .withCanReadPartitionCoefficients(
                        policies.supply().canReadPartitionCoefficients(caller, supply).isAllowed())
                .withCanCreatePlant(policies.plant().canCreate(caller, supply).isAllowed())
                .build();
    }
}
