package org.lucoenergia.conluz.domain.consumption.shelly;

import java.time.Instant;

public class ShellyInstantConsumption {

    private final Instant timestamp;
    private final Double consumptionKW;
    private final String channel;
    private final String prefix;

    private ShellyInstantConsumption(Builder builder) {
        this.timestamp = builder.timestamp;
        this.consumptionKW = builder.consumptionKW;
        this.channel = builder.channel;
        this.prefix = builder.prefix;
    }

    public static class Builder {

        private Instant timestamp;
        private Double consumptionKW;
        private String channel;
        private String prefix;

        public Builder withTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withConsumptionKW(Double consumptionKWh) {
            this.consumptionKW = consumptionKWh;
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

    public Double getConsumptionKW() {
        return consumptionKW;
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
                ", consumptionKW=" + consumptionKW +
                ", channel=" + channel +
                ", prefix='" + prefix + '\'' +
                '}';
    }
}