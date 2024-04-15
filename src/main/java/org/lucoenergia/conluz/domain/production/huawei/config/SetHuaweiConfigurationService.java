package org.lucoenergia.conluz.domain.production.huawei.config;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.springframework.stereotype.Service;

@Service
public class SetHuaweiConfigurationService {

    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    public SetHuaweiConfigurationService(SetHuaweiConfigurationRepository setHuaweiConfigurationRepository) {
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
    }

    public HuaweiConfig setHuaweiConfiguration(HuaweiConfig config) {
        return setHuaweiConfigurationRepository.setHuaweiConfiguration(config);
    }
}
