package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

@Schema(requiredProperties = {"enabled"})
public class ConfigureShellyBody {

    @NotNull
    private Boolean enabled;

    public ConfigureShellyBody(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public ShellyConfig toShellyConfig() {
        return new ShellyConfig.Builder()
                .setEnabled(enabled)
                .build();
    }
}
