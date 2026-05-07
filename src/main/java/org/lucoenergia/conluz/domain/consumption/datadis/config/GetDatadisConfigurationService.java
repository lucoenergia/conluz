package org.lucoenergia.conluz.domain.consumption.datadis.config;

import java.util.Optional;

public interface GetDatadisConfigurationService {

    boolean isDisabled();

    Optional<DatadisConfig> getDatadisConfiguration();
}
