package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

public class GetShellyConfigResponse {

    private Boolean enabled;

    public GetShellyConfigResponse(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public static GetShellyConfigResponse of(ShellyConfig config) {
        return new GetShellyConfigResponse(Boolean.TRUE.equals(config.getEnabled()));
    }
}
