package org.lucoenergia.conluz.domain.datadis;

import java.util.UUID;

public interface SetDatadisConfigurationService {

    DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config);
}
