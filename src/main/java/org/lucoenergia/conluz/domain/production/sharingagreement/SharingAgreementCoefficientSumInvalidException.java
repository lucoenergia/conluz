package org.lucoenergia.conluz.domain.production.sharingagreement;

import java.math.BigDecimal;
import java.util.UUID;

public class SharingAgreementCoefficientSumInvalidException extends RuntimeException {

    private final UUID id;
    private final BigDecimal actualSum;

    public SharingAgreementCoefficientSumInvalidException(UUID id, BigDecimal actualSum) {
        this.id = id;
        this.actualSum = actualSum;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getActualSum() {
        return actualSum;
    }
}
