package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.GetHuaweiConfigurationService;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
        Optional<HuaweiConfig> config = getHuaweiConfigRepository.getHuaweiConfig();
        if (config.isEmpty()) {
            LOGGER.info("No Huawei config found.");
            return true;
        }
        if (!Boolean.TRUE.equals(config.get().getEnabled())) {
            LOGGER.info("Huawei integration is disabled.");
            return true;
        }
        return false;
    }
}
