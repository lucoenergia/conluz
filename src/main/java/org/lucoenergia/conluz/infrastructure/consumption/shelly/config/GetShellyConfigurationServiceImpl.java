package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import org.lucoenergia.conluz.domain.consumption.shelly.config.GetShellyConfigurationService;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetShellyConfigurationServiceImpl implements GetShellyConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetShellyConfigurationServiceImpl.class);

    private final GetShellyConfigRepository getShellyConfigRepository;

    public GetShellyConfigurationServiceImpl(GetShellyConfigRepository getShellyConfigRepository) {
        this.getShellyConfigRepository = getShellyConfigRepository;
    }

    @Override
    public boolean isDisabled() {
        Optional<ShellyConfig> config = getShellyConfigRepository.getShellyConfig();
        if (config.isEmpty()) {
            LOGGER.info("No Shelly config found.");
            return true;
        }
        if (!Boolean.TRUE.equals(config.get().getEnabled())) {
            LOGGER.info("Shelly integration is disabled.");
            return true;
        }
        return false;
    }
}
