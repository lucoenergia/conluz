package org.lucoenergia.conluz.domain.consumption.shelly.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetShellyConfigurationService {

    Optional<ShellyConfig> getShellyConfiguration();

    Optional<ShellyConfig> getShellyConfiguration(UUID communityId);

    List<ShellyConfig> getEnabledShellyConfigurations();

    boolean isDisabled();
}
