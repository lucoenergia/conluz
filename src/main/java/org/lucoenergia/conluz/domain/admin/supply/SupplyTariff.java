package org.lucoenergia.conluz.domain.admin.supply;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.lucoenergia.conluz.infrastructure.shared.uuid.ValidUUID;

import java.util.Objects;
import java.util.UUID;

public class SupplyTariff {

    @ValidUUID
    private final UUID id;

    @NotNull
    private final Supply supply;

    @PositiveOrZero
    private final Double valley;

    @PositiveOrZero
    private final Double peak;

    @PositiveOrZero
    private final Double offPeak;

    private SupplyTariff(Builder builder) {
        this.id = builder.id;
        if (builder.supply == null || builder.supply.getId() == null) {
            throw new IllegalArgumentException("Supply tariff must be associated with a supply");
        }
        this.supply = builder.supply;

        if (builder.valley == null || builder.peak == null || builder.offPeak == null) {
            throw new IllegalArgumentException("Supply tariff must have all tariff values");
        }
        if (builder.valley < 0 || builder.peak < 0 || builder.offPeak < 0) {
            throw new IllegalArgumentException("Supply tariff tariff values must be positive");
        }
        this.valley = builder.valley;
        this.peak = builder.peak;
        this.offPeak = builder.offPeak;
    }

    public static class Builder {
        private UUID id;
        private Supply supply;
        private Double valley = 0.0;
        private Double peak = 0.0;
        private Double offPeak = 0.0;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withSupply(Supply supply) {
            this.supply = supply;
            return this;
        }

        public Builder withValley(Double valley) {
            this.valley = valley;
            return this;
        }

        public Builder withPeak(Double peak) {
            this.peak = peak;
            return this;
        }

        public Builder withOffPeak(Double offPeak) {
            this.offPeak = offPeak;
            return this;
        }

        public SupplyTariff build() {
            return new SupplyTariff(this);
        }
    }

    public UUID getId() {
        return id;
    }

    public Supply getSupply() {
        return supply;
    }

    public Double getValley() {
        return valley;
    }

    public Double getPeak() {
        return peak;
    }

    public Double getOffPeak() {
        return offPeak;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplyTariff that = (SupplyTariff) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SupplyTariff{" +
                "id=" + id +
                ", supplyId=" + supply.getId() +
                ", valley=" + valley +
                ", peak=" + peak +
                ", offPeak=" + offPeak +
                '}';
    }
}