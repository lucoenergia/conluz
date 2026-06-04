package org.lucoenergia.conluz.domain.consumption.datadis.get;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetDatadisConfigRepository {

    Optional<DatadisConfig> getDatadisConfig();

    Optional<DatadisConfig> getDatadisConfig(UUID communityId);

    List<DatadisConfig> getEnabledDatadisConfigs();
}
