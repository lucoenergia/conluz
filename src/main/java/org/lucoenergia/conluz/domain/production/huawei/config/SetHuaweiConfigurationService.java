package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.UUID;

public interface SetHuaweiConfigurationService {

    /**
     * Creates or replaces the plant's Huawei configuration. When the command supplies no password, the
     * stored one is kept.
     *
     * @throws HuaweiConfigPasswordRequiredException if the command supplies no password and the plant has
     *                                               no stored non-empty password to keep
     */
    HuaweiConfig setHuaweiConfiguration(UUID plantId, SetHuaweiConfigurationCommand command);
}
