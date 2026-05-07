package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class GetDatadisConfigurationServiceImpl implements GetDatadisConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDatadisConfigurationServiceImpl.class);

    private final GetDatadisConfigRepository getDatadisConfigRepository;

    public GetDatadisConfigurationServiceImpl(GetDatadisConfigRepository getDatadisConfigRepository) {
        this.getDatadisConfigRepository = getDatadisConfigRepository;
    }

    @Override
    public boolean isDisabled() {
        Optional<DatadisConfig> config = getDatadisConfigRepository.getDatadisConfig();
        if (config.isEmpty()) {
            LOGGER.info("No Datadis config found.");
            return true;
        }
        if (!Boolean.TRUE.equals(config.get().getEnabled())) {
            LOGGER.info("Datadis integration is disabled.");
            return true;
        }
        return false;
    }

    @Override
    public Optional<DatadisConfig> getDatadisConfiguration() {
        return getDatadisConfigRepository.getDatadisConfig();
    }
}
