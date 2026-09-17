package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SupplyPartitionCoefficientDetailMother {

    /**
     * Wraps an existing coefficient in plausible references, for tests that care about the
     * coefficient itself rather than about which supply, community, plant or agreement it points at.
     */
    public static SupplyPartitionCoefficientDetail of(SupplyPartitionCoefficient coefficient) {
        return new SupplyPartitionCoefficientDetail(
                coefficient,
                new SupplyReference(
                        coefficient.getSupplyId() != null ? coefficient.getSupplyId() : UUID.randomUUID(),
                        "ES0031607648137001RC0F", "A supply"),
                new CommunityReference(UUID.randomUUID(), "A community"),
                new PlantReference(
                        coefficient.getPlantId() != null ? coefficient.getPlantId() : UUID.randomUUID(),
                        "A plant"),
                new SharingAgreementReference(
                        coefficient.getSharingAgreementId() != null ? coefficient.getSharingAgreementId() : UUID.randomUUID(),
                        "An agreement", SharingAgreementStatus.PUBLISHED));
    }

    public static List<SupplyPartitionCoefficientDetail> of(List<SupplyPartitionCoefficient> coefficients) {
        return coefficients.stream().map(SupplyPartitionCoefficientDetailMother::of).toList();
    }

    public static SupplyPartitionCoefficientDetail random(BigDecimal coefficient, Instant validFrom, Instant validTo) {
        return of(new SupplyPartitionCoefficient.Builder()
                .withId(UUID.randomUUID())
                .withSupplyId(UUID.randomUUID())
                .withPlantId(UUID.randomUUID())
                .withSharingAgreementId(UUID.randomUUID())
                .withCoefficient(coefficient)
                .withValidFrom(validFrom)
                .withValidTo(validTo)
                .withCreatedAt(Instant.now())
                .build());
    }
}
