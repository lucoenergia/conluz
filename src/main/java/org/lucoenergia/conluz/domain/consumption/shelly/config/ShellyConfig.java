package org.lucoenergia.conluz.domain.consumption.shelly.config;

import java.util.UUID;

public class ShellyConfig {

    private UUID id;
    private Boolean enabled;
    private UUID communityId;

    public UUID getId() {
        return id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public UUID getCommunityId() {
        return communityId;
    }

    public static class Builder {
        private UUID id;
        private Boolean enabled;
        private UUID communityId;

        public Builder setId(UUID id) {
            this.id = id;
            return this;
        }

        public Builder setEnabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder setCommunityId(UUID communityId) {
            this.communityId = communityId;
            return this;
        }

        public ShellyConfig build() {
            ShellyConfig shellyConfig = new ShellyConfig();
            shellyConfig.id = this.id;
            shellyConfig.enabled = this.enabled != null ? this.enabled : Boolean.FALSE;
            shellyConfig.communityId = this.communityId;
            return shellyConfig;
        }
    }
}
