package org.lucoenergia.conluz.domain.production.plant.distributorfile;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One successfully-parsed {@code CUPS;coefficient} line. Presence here only means rules 3 and 4
 * passed for this line -- it may still be involved in a rule 5/6/8 violation reported separately.
 */
public class DistributorFileEntry {

    private final String cups;
    private final BigDecimal coefficient;
    private final int lineNumber;

    public DistributorFileEntry(String cups, BigDecimal coefficient, int lineNumber) {
        this.cups = cups;
        this.coefficient = coefficient;
        this.lineNumber = lineNumber;
    }

    public String getCups() {
        return cups;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DistributorFileEntry that)) return false;
        return lineNumber == that.lineNumber && Objects.equals(cups, that.cups) &&
                Objects.equals(coefficient, that.coefficient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cups, coefficient, lineNumber);
    }

    @Override
    public String toString() {
        return "DistributorFileEntry{" +
                "cups='" + cups + '\'' +
                ", coefficient=" + coefficient +
                ", lineNumber=" + lineNumber +
                '}';
    }
}
