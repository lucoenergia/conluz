package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One {supplyId, coefficient} pair to materialise as a pending coefficient row, for callers that
 * have already identified the supply by its internal id rather than by CUPS (e.g. the manual
 * authoring PUT path). The supplyId-keyed sibling of {@link PendingCoefficientEntry}.
 */
public class ResolvedCoefficientEntry {

    private final UUID supplyId;
    private final BigDecimal coefficient;

    public ResolvedCoefficientEntry(UUID supplyId, BigDecimal coefficient) {
        this.supplyId = supplyId;
        this.coefficient = coefficient;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }
}
