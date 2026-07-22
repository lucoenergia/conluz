package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaveSupplyPartitionCoefficientRepository {

    SupplyPartitionCoefficient save(SupplyPartitionCoefficient coefficient);

    void closeActivePeriod(UUID supplyId, UUID plantId, Instant validTo);

    void syncSupplyPartitionCoefficient(UUID supplyId, BigDecimal newCoefficient);

    /**
     * Atomically replaces every row belonging to {@code sharingAgreementId} (pending or not) with
     * {@code coefficients}: deletes the agreement's entire existing set, then inserts each element
     * of {@code coefficients} as a new row, in one transaction. Every element is persisted exactly
     * as given -- this method performs no status check and does not touch validFrom/validTo itself.
     * Callers must call {@link org.lucoenergia.conluz.domain.production.plant.sharingagreement.SharingAgreement#assertDraft()}
     * before invoking this.
     */
    List<SupplyPartitionCoefficient> replaceAllForSharingAgreement(UUID sharingAgreementId,
                                                                     List<SupplyPartitionCoefficient> coefficients);
}
