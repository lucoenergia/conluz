package org.lucoenergia.conluz.domain.consumption.shelly;

import java.time.Instant;

public class ShellyInstantConsumption {

    private final Instant timestamp;
    private final Double consumptionKWh;
    private final String channel;
    private final String prefix;

    private ShellyInstantConsumption(Builder builder) {
        this.timestamp = builder.timestamp;
        this.consumptionKWh = builder.consumptionKWh;
        this.channel = builder.channel;
        this.prefix = builder.prefix;
    }

    public static class Builder {

        private Instant timestamp;
        private Double consumptionKWh;
        private String channel;
        private String prefix;

        public Builder withTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withConsumptionKWh(Double consumptionKWh) {
            this.consumptionKWh = consumptionKWh;
            return this;
        }

        public Builder withChannel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder withPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public ShellyInstantConsumption build() {
            return new ShellyInstantConsumption(this);
        }
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getConsumptionKWh() {
        return consumptionKWh;
    }

    public String getChannel() {
        return channel;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    public String toString() {
        return "ShellyConsumption{" +
                "timestamp=" + timestamp +
                ", consumptionKWh=" + consumptionKWh +
                ", channel=" + channel +
                ", prefix='" + prefix + '\'' +
                '}';
    }
}