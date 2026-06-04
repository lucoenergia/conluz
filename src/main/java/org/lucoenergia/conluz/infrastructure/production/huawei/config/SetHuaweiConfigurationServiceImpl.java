package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class SetHuaweiConfigurationServiceImpl implements SetHuaweiConfigurationService {

    private final SetHuaweiConfigurationRepository setHuaweiConfigurationRepository;

    public SetHuaweiConfigurationServiceImpl(SetHuaweiConfigurationRepository setHuaweiConfigurationRepository) {
        this.setHuaweiConfigurationRepository = setHuaweiConfigurationRepository;
    }

    @Override
    public HuaweiConfig setHuaweiConfiguration(HuaweiConfig config) {
        return setHuaweiConfigurationRepository.setHuaweiConfiguration(config);
    }

    @Override
    public HuaweiConfig setHuaweiConfiguration(UUID plantId, HuaweiConfig config) {
        return setHuaweiConfigurationRepository.setHuaweiConfiguration(plantId, config);
    }
}
