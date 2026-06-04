package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetHuaweiConfigurationServiceImpl implements GetHuaweiConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetHuaweiConfigurationServiceImpl.class);

    private final GetHuaweiConfigRepository getHuaweiConfigRepository;

    public GetHuaweiConfigurationServiceImpl(GetHuaweiConfigRepository getHuaweiConfigRepository) {
        this.getHuaweiConfigRepository = getHuaweiConfigRepository;
    }

    @Override
    public boolean isDisabled() {
        List<HuaweiConfig> enabledConfigs = getHuaweiConfigRepository.getEnabledHuaweiConfigs();
        if (enabledConfigs.isEmpty()) {
            LOGGER.info("No enabled Huawei config found.");
            return true;
        }
        return false;
    }

    @Override
    public Optional<HuaweiConfig> getHuaweiConfiguration() {
        return getHuaweiConfigRepository.getHuaweiConfig();
    }

    @Override
    public Optional<HuaweiConfig> getHuaweiConfiguration(UUID plantId) {
        return getHuaweiConfigRepository.getHuaweiConfig(plantId);
    }

    @Override
    public List<HuaweiConfig> getEnabledHuaweiConfigurations() {
        return getHuaweiConfigRepository.getEnabledHuaweiConfigs();
    }
}
