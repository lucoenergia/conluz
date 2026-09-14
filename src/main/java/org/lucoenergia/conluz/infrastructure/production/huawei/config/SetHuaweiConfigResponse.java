package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

@Schema(requiredProperties = {"username", "passwordSet", "baseUrl", "enabled"})
public class SetHuaweiConfigResponse {

    private final String username;
    private final boolean passwordSet;
    private final String baseUrl;
    private final boolean enabled;

    private SetHuaweiConfigResponse(String username, boolean passwordSet, String baseUrl, boolean enabled) {
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

    public static SetHuaweiConfigResponse of(HuaweiConfig config) {
        return new SetHuaweiConfigResponse(
                config.getUsername(),
                StringUtils.isNotEmpty(config.getPassword()),
                config.getBaseUrl(),
                Boolean.TRUE.equals(config.getEnabled())
        );
    }
}
