package org.lucoenergia.conluz.domain.production.huawei.get;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetHuaweiConfigRepository {

    Optional<HuaweiConfig> findFirst();

    Optional<HuaweiConfig> getHuaweiConfig(UUID plantId);

    List<HuaweiConfig> getEnabledHuaweiConfigs();

    /**
     * Whether the plant has a Huawei configuration and it is enabled. No credential is read.
     */
    boolean isEnabled(UUID plantId);
}
