package org.lucoenergia.conluz.domain.consumption.datadis.config;

import java.util.UUID;

public interface SetDatadisConfigurationRepository {

    DatadisConfig setDatadisConfiguration(DatadisConfig config);

    DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config);
}
