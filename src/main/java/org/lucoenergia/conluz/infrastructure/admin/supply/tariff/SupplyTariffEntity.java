package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import jakarta.persistence.*;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity for supply tariffs
 */
@Entity
@Table(name = "supplies_tariffs")
public class SupplyTariffEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supply_id")
    private SupplyEntity supply;

    @Column(name = "valley")
    private Double valley = 0.0;

    @Column(name = "peak")
    private Double peak = 0.0;

    @Column(name = "off_peak")
    private Double offPeak = 0.0;

    public SupplyTariffEntity() {
        // Required by JPA
    }

    public static class Builder {
        private UUID id;
        private SupplyEntity supply;
        private Double valley = 0.0;
        private Double peak = 0.0;
        private Double offPeak = 0.0;

        public Builder withId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder withSupply(SupplyEntity supply) {
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

        public SupplyTariffEntity build() {
            SupplyTariffEntity entity = new SupplyTariffEntity();
            entity.setId(this.id);
            entity.setSupply(this.supply);
            entity.setValley(this.valley);
            entity.setPeak(this.peak);
            entity.setOffPeak(this.offPeak);
            return entity;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public SupplyEntity getSupply() {
        return supply;
    }

    public void setSupply(SupplyEntity supply) {
        this.supply = supply;
    }

    public Double getValley() {
        return valley;
    }

    public void setValley(Double valley) {
        this.valley = valley;
    }

    public Double getPeak() {
        return peak;
    }

    public void setPeak(Double peak) {
        this.peak = peak;
    }

    public Double getOffPeak() {
        return offPeak;
    }

    public void setOffPeak(Double offPeak) {
        this.offPeak = offPeak;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SupplyTariffEntity)) return false;
        return id != null && id.equals(((SupplyTariffEntity) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SupplyTariffEntity{" +
                "id=" + id +
                ", valley=" + valley +
                ", peak=" + peak +
                ", offPeak=" + offPeak +
                '}';
    }
}