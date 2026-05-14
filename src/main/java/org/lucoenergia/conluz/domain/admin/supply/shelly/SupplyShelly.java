package org.lucoenergia.conluz.domain.admin.supply.shelly;

public class SupplyShelly {

    private String mac;
    private String id;
    private String mqttPrefix;

    public SupplyShelly() {
    }

    private SupplyShelly(Builder builder) {
        this.mac = builder.mac;
        this.id = builder.id;
        this.mqttPrefix = builder.mqttPrefix;
    }

    public static class Builder {
        private String mac;
        private String id;
        private String mqttPrefix;

        public Builder withMac(String mac) {
            this.mac = mac;
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

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMqttPrefix() {
        return mqttPrefix;
    }

    public void setMqttPrefix(String mqttPrefix) {
        this.mqttPrefix = mqttPrefix;
    }
}
