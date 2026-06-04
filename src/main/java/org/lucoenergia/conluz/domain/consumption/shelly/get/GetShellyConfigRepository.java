package org.lucoenergia.conluz.domain.consumption.shelly.get;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetShellyConfigRepository {

    Optional<ShellyConfig> getShellyConfig();

    Optional<ShellyConfig> getShellyConfig(UUID communityId);

    List<ShellyConfig> getEnabledShellyConfigs();
}
