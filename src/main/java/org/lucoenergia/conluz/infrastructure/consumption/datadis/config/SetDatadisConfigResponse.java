package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.admin.datadis.DatadisConfig;

public class SetDatadisConfigResponse {

    private final String username;
    private final boolean passwordSet;

    private SetDatadisConfigResponse(String username, boolean passwordSet) {
        this.username = username;
        this.passwordSet = passwordSet;
    }

    public String getUsername() {
        return username;
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public static SetDatadisConfigResponse of(DatadisConfig config) {
        return new SetDatadisConfigResponse(config.getUsername(), StringUtils.isNotEmpty(config.getPassword()));
    }
}
