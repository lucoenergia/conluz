package org.lucoenergia.conluz.domain.consumption.datadis.config;

import java.util.UUID;

public interface SetDatadisConfigurationService {

    DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config);
}
