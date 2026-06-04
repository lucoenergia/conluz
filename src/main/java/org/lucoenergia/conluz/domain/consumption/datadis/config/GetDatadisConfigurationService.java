package org.lucoenergia.conluz.domain.consumption.datadis.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetDatadisConfigurationService {

    boolean isDisabled();

    Optional<DatadisConfig> getDatadisConfiguration();

    Optional<DatadisConfig> getDatadisConfiguration(UUID communityId);

    List<DatadisConfig> getEnabledDatadisConfigurations();
}
