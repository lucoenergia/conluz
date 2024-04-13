package org.lucoenergia.conluz.domain.production.huawei.get;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;

import java.util.Optional;

public interface GetHuaweiConfigRepository {

    Optional<HuaweiConfig> getHuaweiConfig();
}
