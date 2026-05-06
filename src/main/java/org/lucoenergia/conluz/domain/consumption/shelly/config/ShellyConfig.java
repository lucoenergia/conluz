package org.lucoenergia.conluz.domain.consumption.shelly.config;

import java.util.UUID;

public class ShellyConfig {

    private UUID id;
    private Boolean enabled;

    public UUID getId() {
        return id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public static class Builder {
        private UUID id;
        private Boolean enabled;

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder setEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ShellyConfig build() {
            ShellyConfig shellyConfig = new ShellyConfig();
            shellyConfig.id = this.id;
            shellyConfig.enabled = this.enabled != null ? this.enabled : Boolean.FALSE;
            return shellyConfig;
        }
    }
}
