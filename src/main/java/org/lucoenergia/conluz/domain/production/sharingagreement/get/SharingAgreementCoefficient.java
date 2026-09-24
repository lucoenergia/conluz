package org.lucoenergia.conluz.domain.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientApplicationState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficientDetail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of a sharing agreement's coefficient set, enriched with the supply's display data, the
 * two server-computed states so callers never need to derive interval/successor logic, and the
 * coefficient the supply is currently on in this agreement's plant.
 */
public class SharingAgreementCoefficient {

    private final SupplyPartitionCoefficient coefficient;
    private final Supply supply;
    private final CoefficientApplicationState applicationState;
    private final CoefficientEndState endState;
    private final Instant endDate;
    private final SupplyPartitionCoefficientDetail currentCoefficient;

    public SharingAgreementCoefficient(SupplyPartitionCoefficient coefficient, Supply supply,
                                       CoefficientApplicationState applicationState,
                                       CoefficientEndState endState, Instant endDate,
                                       SupplyPartitionCoefficientDetail currentCoefficient) {
        this.coefficient = coefficient;
        this.supply = supply;
        this.applicationState = applicationState;
        this.endState = endState;
        this.endDate = endDate;
        this.currentCoefficient = currentCoefficient;
    }

    public UUID getCoefficientId() {
        return coefficient.getId();
    }

    public UUID getSupplyId() {
        return supply.getId();
    }

    public String getSupplyCode() {
        return supply.getCode();
    }

    public String getSupplyName() {
        return supply.getName();
    }

    public BigDecimal getCoefficient() {
        return coefficient.getCoefficient();
    }

    public Instant getValidFrom() {
        return coefficient.getValidFrom();
    }

    public Instant getValidTo() {
        return coefficient.getValidTo();
    }

    public CoefficientApplicationState getApplicationState() {
        return applicationState;
    }

    public CoefficientEndState getEndState() {
        return endState;
    }

    public Instant getEndDate() {
        return endDate;
    }

    /**
     * The coefficient this supply is currently on in the agreement's own plant, or null when it has
     * none there. Scoped to that plant: a supply active in several plants has a different current
     * coefficient in each, and only the agreement's own is meaningful here.
     *
     * <p>Null covers two distinct cases the caller does not need to tell apart: the supply has never
     * had an activated coefficient in this plant, or its last one was explicitly closed.
     */
    public SupplyPartitionCoefficientDetail getCurrentCoefficient() {
        return currentCoefficient;
    }
}
