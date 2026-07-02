package org.lucoenergia.conluz.infrastructure.datadis.config;

import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationService;
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
