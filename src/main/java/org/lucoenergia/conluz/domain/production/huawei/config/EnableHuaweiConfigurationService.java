package org.lucoenergia.conluz.domain.production.huawei.config;

import java.util.UUID;

public interface EnableHuaweiConfigurationService {

    /**
     * Enables the plant's Huawei configuration, changing nothing else. The stored configuration must be complete.
     * Enabling a configuration that is already enabled succeeds.
     *
     * @throws HuaweiConfigNotFoundException   if the plant has no configuration
     * @throws HuaweiConfigIncompleteException if the stored configuration lacks a non-blank username, a valid base
     *                                         URL or a non-empty password
     */
    void enable(UUID plantId);
}
