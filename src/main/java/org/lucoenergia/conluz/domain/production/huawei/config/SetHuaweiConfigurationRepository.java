package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.UUID;

public interface SetHuaweiConfigurationRepository {

    HuaweiConfig setHuaweiConfiguration(UUID plantId, HuaweiConfig config);

    /**
     * Sets only whether the plant's configuration is enabled, leaving every other field as stored.
     *
     * @return {@code false} when the plant has no configuration, in which case nothing is written
     */
    boolean setEnabled(UUID plantId, boolean enabled);
}
