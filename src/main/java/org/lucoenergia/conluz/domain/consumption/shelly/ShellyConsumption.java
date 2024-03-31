package org.lucoenergia.conluz.domain.consumption.shelly;

import java.time.Instant;

public class ShellyConsumption {

    private final Instant timestamp;
    private final Double consumptionKWh;
    private final String prefix;

    private ShellyConsumption(Builder builder) {
        this.timestamp = builder.timestamp;
        this.consumptionKWh = builder.consumptionKWh;
        this.prefix = builder.prefix;
    }

    public static class Builder {

        private Instant timestamp;
        private Double consumptionKWh;
        private String prefix;

        public Builder withTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withConsumptionKWh(Double consumptionKWh) {
            this.consumptionKWh = consumptionKWh;
            return this;
        }

        public Builder withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public ShellyConsumption build() {
            return new ShellyConsumption(this);
        }
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getConsumptionKWh() {
        return consumptionKWh;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public String toString() {
        return "ShellyConsumption{" +
                "timestamp=" + timestamp +
                ", consumptionKWh=" + consumptionKWh +
                ", prefix='" + prefix + '\'' +
                '}';
    }
}