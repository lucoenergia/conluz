package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.UUID;

public interface SetHuaweiConfigurationService {

    /**
     * Creates or replaces the plant's Huawei configuration. When no password is supplied, the
     * stored one is kept.
     *
     * @throws HuaweiConfigPasswordRequiredException if no password is supplied and the plant has
     *                                               no stored non-empty password to keep
     */
    HuaweiConfig setHuaweiConfiguration(UUID plantId, SetHuaweiConfiguration configuration);
}
