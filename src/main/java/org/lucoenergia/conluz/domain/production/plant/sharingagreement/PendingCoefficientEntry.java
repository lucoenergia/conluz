package org.lucoenergia.conluz.domain.production.plant.sharingagreement;

import java.math.BigDecimal;

/**
 * One {cups, coefficient} pair to materialise as a pending coefficient row. The shared shape
 * between a parsed distributor-file entry (dropping its file-specific line number, which has no
 * meaning once decoupled from a specific upload) and the manual-authoring PUT path.
 */
public class PendingCoefficientEntry {

    private final String cups;
    private final BigDecimal coefficient;

    public PendingCoefficientEntry(String cups, BigDecimal coefficient) {
        this.cups = cups;
        this.coefficient = coefficient;
    }

    public String getCups() {
        return cups;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }
}
