package org.lucoenergia.conluz.domain.consumption.shelly.config;

import java.util.Optional;
import java.util.UUID;

public interface GetShellyConfigurationService {

    Optional<ShellyConfig> getShellyConfiguration(UUID communityId);

    boolean isDisabled();
}
