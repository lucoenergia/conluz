package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One coefficient period enriched with the display data of the supply, its community, and the plant
 * and sharing agreement it refers to, so a caller can render a timeline without resolving four
 * further references itself.
 *
 * <p>{@code community} is the community of the supply the coefficient belongs to, not that of the
 * plant it applies in.
 *
 * <p>The flat constructor exists for JPQL constructor expressions: selecting scalars directly means no
 * entity is materialised, so neither a lazy proxy nor an eager {@code @OneToOne} can fire, and a whole
 * result set costs exactly one query. Assembling the nested references here rather than in the query
 * keeps that detail out of every call site.
 */
public class SupplyPartitionCoefficientDetail {

    private final SupplyPartitionCoefficient coefficient;
    private final SupplyReference supply;
    private final CommunityReference community;
    private final PlantReference plant;
    private final SharingAgreementReference sharingAgreement;

    public SupplyPartitionCoefficientDetail(SupplyPartitionCoefficient coefficient, SupplyReference supply,
                                            CommunityReference community, PlantReference plant,
                                            SharingAgreementReference sharingAgreement) {
        this.coefficient = coefficient;
        this.supply = supply;
        this.community = community;
        this.plant = plant;
        this.sharingAgreement = sharingAgreement;
    }

    /**
     * Flat constructor for JPQL constructor expressions. Argument order matches the SELECT clause of
     * the queries in {@code SupplyPartitionCoefficientJpaRepository}.
     */
    public SupplyPartitionCoefficientDetail(UUID id,
                                            UUID supplyId, String supplyCode, String supplyName,
                                            UUID communityId, String communityName,
                                            UUID plantId, String plantName,
                                            UUID sharingAgreementId, String sharingAgreementName,
                                            SharingAgreementStatus sharingAgreementStatus,
                                            BigDecimal coefficient, Instant validFrom, Instant validTo,
                                            Instant createdAt) {
        this(new SupplyPartitionCoefficient.Builder()
                        .withId(id)
                        .withSupplyId(supplyId)
                        .withPlantId(plantId)
                        .withSharingAgreementId(sharingAgreementId)
                        .withCoefficient(coefficient)
                        .withValidFrom(validFrom)
                        .withValidTo(validTo)
                        .withCreatedAt(createdAt)
                        .build(),
                new SupplyReference(supplyId, supplyCode, supplyName),
                new CommunityReference(communityId, communityName),
                new PlantReference(plantId, plantName),
                new SharingAgreementReference(sharingAgreementId, sharingAgreementName, sharingAgreementStatus));
    }

    public SupplyPartitionCoefficient getCoefficient() {
        return coefficient;
    }

    public SupplyReference getSupply() {
        return supply;
    }

    public CommunityReference getCommunity() {
        return community;
    }

    public PlantReference getPlant() {
        return plant;
    }

    public SharingAgreementReference getSharingAgreement() {
        return sharingAgreement;
    }

    public UUID getId() {
        return coefficient.getId();
    }

    public BigDecimal getCoefficientValue() {
        return coefficient.getCoefficient();
    }

    public Instant getValidFrom() {
        return coefficient.getValidFrom();
    }

    public Instant getValidTo() {
        return coefficient.getValidTo();
    }

    public Instant getCreatedAt() {
        return coefficient.getCreatedAt();
    }
}
