package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

/**
 * What the caller may do with a plant, decided over the plant already in hand — including the
 * supply hanging off it, so a page of plants costs no queries.
 *
 * <p>{@code canListSharingAgreements} and {@code canManageSharingAgreements} are both
 * {@code PlantAccessPolicy.canManage}, which is what the guards {@code canListSharingAgreements}
 * and {@code canManageSharingAgreement(plantId)} delegate to.</p>
 *
 * <p>{@code canReadSupply} exists because {@code PlantResponse.supply} is a reference and
 * references carry no capabilities: listing plants is open to any member, but the supply behind one
 * is not, so the plant says whether following the reference would succeed.</p>
 */
@Component
public class PlantCapabilitiesAssembler {

    private final AccessPolicies policies;

    public PlantCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public PlantCapabilitiesResponse assemble(User caller, Plant plant) {
        boolean canManage = policies.plant().canManage(caller, plant).isAllowed();

        return PlantCapabilitiesResponse.builder()
                .withCanRead(policies.plant().canRead(caller, plant).isAllowed())
                .withCanManage(canManage)
                .withCanListSharingAgreements(canManage)
                .withCanManageSharingAgreements(canManage)
                .withCanReadSupply(policies.supply().canRead(caller, plant.getSupply()).isAllowed())
                .build();
    }
}
