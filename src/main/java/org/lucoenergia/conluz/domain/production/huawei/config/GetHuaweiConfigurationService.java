package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.Optional;

public interface GetHuaweiConfigurationService {

    boolean isDisabled();

    Optional<HuaweiConfig> getHuaweiConfiguration();
}
