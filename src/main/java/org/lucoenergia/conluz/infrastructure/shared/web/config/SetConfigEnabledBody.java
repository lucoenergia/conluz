package org.lucoenergia.conluz.infrastructure.shared.web.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Whether a provider configuration is enabled (#414). Shared by every provider's {@code enabled} sub-resource.
 */
@Schema(requiredProperties = {
        "enabled"
})
public class SetConfigEnabledBody {

    @NotNull
    private Boolean enabled;

    public SetConfigEnabledBody() {
    }

    public SetConfigEnabledBody(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
