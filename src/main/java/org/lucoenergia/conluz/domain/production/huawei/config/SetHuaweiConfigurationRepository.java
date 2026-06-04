package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.UUID;

public interface SetHuaweiConfigurationRepository {

    HuaweiConfig setHuaweiConfiguration(HuaweiConfig config);

    HuaweiConfig setHuaweiConfiguration(UUID plantId, HuaweiConfig config);
}
