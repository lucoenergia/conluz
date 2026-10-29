package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.SupplyAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyCodeAvailability;
import org.lucoenergia.conluz.domain.admin.supply.SupplyRegisteredInAnotherCommunityException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Runs in the transaction of the write it checks for.
 */
@Component
public class SupplyCodeAvailabilityImpl implements SupplyCodeAvailability {

    private final GetSupplyRepository getSupplyRepository;

    public SupplyCodeAvailabilityImpl(GetSupplyRepository getSupplyRepository) {
        this.getSupplyRepository = getSupplyRepository;
    }

    @Override
    public void requireAvailable(SupplyCode code, UUID communityId) {
        require(getSupplyRepository.findCommunityIdsByUpperCaseCodes(code.sameCupsForms()), communityId);
    }

    @Override
    public void requireAvailable(SupplyCode code, UUID communityId, SupplyId supplyId) {
        require(getSupplyRepository.findCommunityIdsByUpperCaseCodes(code.sameCupsForms(), supplyId), communityId);
    }

    private static void require(Set<UUID> communitiesWithTheCups, UUID communityId) {
        if (communitiesWithTheCups.contains(communityId)) {
            throw new SupplyAlreadyExistsException();
        }
        if (!communitiesWithTheCups.isEmpty()) {
            throw new SupplyRegisteredInAnotherCommunityException();
        }
    }
}
