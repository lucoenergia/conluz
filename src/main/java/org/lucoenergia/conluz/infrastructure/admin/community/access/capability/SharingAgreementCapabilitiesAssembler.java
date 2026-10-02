package org.lucoenergia.conluz.infrastructure.admin.community.access.capability;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.infrastructure.admin.community.access.AccessPolicies;
import org.springframework.stereotype.Component;

/**
 * What the caller may do with a sharing agreement.
 *
 * <p>An agreement is always reached through its plant, and whether the caller may see that plant is
 * half the decision — so this asks the composed {@code ...ThroughPlant} rules rather than the
 * agreement-only ones. The plant is passed in because the agreement carries only a
 * {@code plantId}; a listing loads it once for the whole page.</p>
 */
@Component
public class SharingAgreementCapabilitiesAssembler {

    private final AccessPolicies policies;

    public SharingAgreementCapabilitiesAssembler(AccessPolicies policies) {
        this.policies = policies;
    }

    public SharingAgreementCapabilitiesResponse assemble(User caller, Plant plant, SharingAgreement agreement) {
        return SharingAgreementCapabilitiesResponse.builder()
                .withCanRead(policies.sharingAgreement()
                        .canReadThroughPlant(caller, plant, agreement).isAllowed())
                .withCanManage(policies.sharingAgreement()
                        .canManageThroughPlant(caller, plant, agreement).isAllowed())
                .build();
    }
}
