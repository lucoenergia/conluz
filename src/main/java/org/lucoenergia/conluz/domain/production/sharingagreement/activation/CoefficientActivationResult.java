package org.lucoenergia.conluz.domain.production.sharingagreement.activation;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;

import java.util.List;

/**
 * What a write of coefficients' validFrom changed.
 *
 * @param touched         every coefficient actually written, deduplicated: the requested ones, the rows their placement
 *                        cascaded onto, and the leaving closures
 * @param leavingClosures the subset of {@code touched} closed because their supply is absent from the agreement
 *                        (SHA-009); empty except on an agreement's first activation
 */
public record CoefficientActivationResult(List<SupplyPartitionCoefficient> touched,
                                          List<SupplyPartitionCoefficient> leavingClosures) {

    public CoefficientActivationResult {
        touched = List.copyOf(touched);
        leavingClosures = List.copyOf(leavingClosures);
    }
}
