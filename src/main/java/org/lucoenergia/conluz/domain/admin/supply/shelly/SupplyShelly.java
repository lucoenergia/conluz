package org.lucoenergia.conluz.domain.admin.supply.shelly;

public class SupplyShelly {

    private final String macAddress;
    private final String id;
    private final String mqttPrefix;

    private SupplyShelly(Builder builder) {
        this.macAddress = builder.macAddress;
        this.id = builder.id;
        this.mqttPrefix = builder.mqttPrefix;
    }

    public static class Builder {
        private String macAddress;
        private String id;
        private String mqttPrefix;

        public Builder withMacAddress(String macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public Builder withId(String id) {
            this.id = id;
            return this;
        }

        public Builder withMqttPrefix(String mqttPrefix) {
            this.mqttPrefix = mqttPrefix;
            return this;
        }

        public SupplyShelly build() {
            return new SupplyShelly(this);
        }
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getId() {
        return id;
    }

    public String getMqttPrefix() {
        return mqttPrefix;
    }
}
