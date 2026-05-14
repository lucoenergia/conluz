package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.get.SupplyPartitionWithComparison;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class SupplyPartitionWithComparisonResponse {

    private final UUID supplyId;
    private final String cups;
    private final String supplyName;
    private final String address;
    private final String userFullName;
    private final BigDecimal coefficient;
    private final BigDecimal previousCoefficient;
    private final BigDecimal delta;

    public SupplyPartitionWithComparisonResponse(SupplyPartitionWithComparison domain) {
        this.supplyId = domain.getSupplyId();
        this.cups = domain.getCups();
        this.supplyName = domain.getSupplyName();
        this.address = domain.getAddress();
        this.userFullName = domain.getUserFullName();
        this.coefficient = BigDecimal.valueOf(domain.getCoefficient())
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        if (domain.getPreviousCoefficient() != null) {
            this.previousCoefficient = BigDecimal.valueOf(domain.getPreviousCoefficient())
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            this.delta = this.coefficient.subtract(this.previousCoefficient);
        } else {
            this.previousCoefficient = null;
            this.delta = null;
        }
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public String getCups() {
        return cups;
    }

    public String getSupplyName() {
        return supplyName;
    }

    public String getAddress() {
        return address;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public BigDecimal getPreviousCoefficient() {
        return previousCoefficient;
    }

    public BigDecimal getDelta() {
        return delta;
    }
}
