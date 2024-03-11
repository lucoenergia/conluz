package org.lucoenergia.conluz.domain.admin.datadis;

import org.springframework.stereotype.Service;

@Service
public class SetDatadisConfigurationService {

    private final SetDatadisConfigurationRepository setDatadisConfigurationRepository;

    public SetDatadisConfigurationService(SetDatadisConfigurationRepository setDatadisConfigurationRepository) {
        this.setDatadisConfigurationRepository = setDatadisConfigurationRepository;
    }

    public DatadisConfig setDatadisConfiguration(DatadisConfig config) {
        return setDatadisConfigurationRepository.setDatadisConfiguration(config);
    }
}
