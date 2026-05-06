package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

public class SetShellyConfigResponse {

    private Boolean enabled;

    public SetShellyConfigResponse(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public static SetShellyConfigResponse of(ShellyConfig config) {
        return new SetShellyConfigResponse(config.getEnabled());
    }
}
