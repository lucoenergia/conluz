package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SupplyPartitionCoefficient {

    private final UUID id;
    private final UUID supplyId;
    private final BigDecimal coefficient;
    private final Instant validFrom;
    private final Instant validTo;
    private final Instant createdAt;

    private SupplyPartitionCoefficient(Builder builder) {
        this.id = builder.id;
        this.supplyId = builder.supplyId;
        this.coefficient = builder.coefficient;
        this.validFrom = builder.validFrom;
        this.validTo = builder.validTo;
        this.createdAt = builder.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public BigDecimal getCoefficient() {
        return coefficient;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return validTo == null;
    }

    public static class Builder {
        private UUID id;
        private UUID supplyId;
        private BigDecimal coefficient;
        private Instant validFrom;
        private Instant validTo;
        private Instant createdAt;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withSupplyId(UUID supplyId) {
            this.supplyId = supplyId;
            return this;
        }

        public Builder withCoefficient(BigDecimal coefficient) {
            this.coefficient = coefficient;
            return this;
        }

        public Builder withValidFrom(Instant validFrom) {
            this.validFrom = validFrom;
            return this;
        }

        public Builder withValidTo(Instant validTo) {
            this.validTo = validTo;
            return this;
        }

        public Builder withCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public SupplyPartitionCoefficient build() {
            return new SupplyPartitionCoefficient(this);
        }
    }
}
