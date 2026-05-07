package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;

public class GetDatadisConfigResponse {

    private final String username;
    private final boolean passwordSet;
    private final String baseUrl;
    private final boolean enabled;

    private GetDatadisConfigResponse(String username, boolean passwordSet, String baseUrl, boolean enabled) {
        this.username = username;
        this.passwordSet = passwordSet;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static GetDatadisConfigResponse of(DatadisConfig config) {
        return new GetDatadisConfigResponse(
                config.getUsername(),
                StringUtils.isNotEmpty(config.getPassword()),
                config.getBaseUrl(),
                Boolean.TRUE.equals(config.getEnabled())
        );
    }
}
