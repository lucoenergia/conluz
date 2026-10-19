package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetHuaweiConfigurationService {

    boolean isDisabled();

    List<HuaweiConfig> findAllEnabled();

    Optional<HuaweiConfig> getHuaweiConfiguration(UUID plantId);
}
