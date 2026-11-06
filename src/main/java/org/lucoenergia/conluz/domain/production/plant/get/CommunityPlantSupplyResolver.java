package org.lucoenergia.conluz.domain.production.plant.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.UUID;

/**
 * Resolves the {@code supplyId} a community production read receives, so that every such read
 * accepts the same supplies: only the backing supply of a plant of the community.
 */
public interface CommunityPlantSupplyResolver {

    /**
     * The supply identified by {@code supplyId}, when it backs a plant of the community.
     *
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException when the supply
     *         does not exist or does not back a plant of the community, so that the existence of a
     *         supply the read does not cover is never confirmed
     */
    Supply resolve(UUID communityId, SupplyId supplyId);
}
