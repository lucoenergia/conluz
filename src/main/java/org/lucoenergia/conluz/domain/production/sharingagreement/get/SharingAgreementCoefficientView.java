package org.lucoenergia.conluz.domain.production.sharingagreement.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientApplicationState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientEndState;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of a sharing agreement's coefficient set, enriched with the supply's display data and
 * the two server-computed states so callers never need to derive interval/successor logic.
 */
public class SharingAgreementCoefficientView {

    private final SupplyPartitionCoefficient coefficient;
    private final Supply supply;
    private final CoefficientApplicationState applicationState;
    private final CoefficientEndState endState;
    private final Instant endDate;

    public SharingAgreementCoefficientView(SupplyPartitionCoefficient coefficient, Supply supply,
                                            CoefficientApplicationState applicationState,
                                            CoefficientEndState endState, Instant endDate) {
        this.coefficient = coefficient;
        this.supply = supply;
        this.applicationState = applicationState;
        this.endState = endState;
        this.endDate = endDate;
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
}
