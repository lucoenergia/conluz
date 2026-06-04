package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.config.SetDatadisConfigurationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Service
public class SetDatadisConfigurationServiceImpl implements SetDatadisConfigurationService {

    private final SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    public SetDatadisConfigurationServiceImpl(SetDatadisConfigurationRepository setDatadisConfigurationRepository) {
        this.setDatadisConfigurationRepository = setDatadisConfigurationRepository;
    }

    @Override
    public DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config) {
        return setDatadisConfigurationRepository.setDatadisConfiguration(communityId, config);
    }
}
