package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface SetDatadisConfigurationRepository {

    DatadisConfig setDatadisConfiguration(DatadisConfig config);

    DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config);
}
