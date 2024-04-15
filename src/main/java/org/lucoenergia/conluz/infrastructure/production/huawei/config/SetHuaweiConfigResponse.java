package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.apache.commons.lang3.StringUtils;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

public class SetHuaweiConfigResponse {

    private final String username;
    private final boolean passwordSet;

    private SetHuaweiConfigResponse(String username, boolean passwordSet) {
        this.username = username;
        this.passwordSet = passwordSet;
    }

    public String getUsername() {
        return username;
    }

    public boolean isPasswordSet() {
        return passwordSet;
    }

    public static SetHuaweiConfigResponse of(HuaweiConfig config) {
        return new SetHuaweiConfigResponse(config.getUsername(), StringUtils.isNotEmpty(config.getPassword()));
    }
}
