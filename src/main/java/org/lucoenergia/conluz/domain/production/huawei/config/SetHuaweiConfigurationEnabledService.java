package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.UUID;

public interface SetHuaweiConfigurationEnabledService {

    /**
     * Enables or disables the plant's Huawei configuration, changing nothing else. Disabling always succeeds;
     * enabling requires the stored configuration to be complete. Setting the value it already has succeeds.
     *
     * @throws HuaweiConfigNotFoundException   if the plant has no configuration
     * @throws HuaweiConfigIncompleteException if enabling and the stored configuration lacks a non-blank
     *                                         username, a valid base URL or a non-empty password
     */
    void setEnabled(UUID plantId, boolean enabled);
}
