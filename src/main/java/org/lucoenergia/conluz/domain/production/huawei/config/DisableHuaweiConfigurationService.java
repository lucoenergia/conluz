package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.UUID;

public interface DisableHuaweiConfigurationService {

    /**
     * Disables the plant's Huawei configuration, changing nothing else, whatever the stored configuration holds.
     * Disabling a configuration that is already disabled succeeds.
     *
     * @throws HuaweiConfigNotFoundException if the plant has no configuration
     */
    void disable(UUID plantId);
}
