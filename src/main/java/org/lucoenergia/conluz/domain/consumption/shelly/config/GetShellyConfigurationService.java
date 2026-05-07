package org.lucoenergia.conluz.domain.consumption.shelly.config;

import java.util.Optional;

public interface GetShellyConfigurationService {

    Optional<ShellyConfig> getShellyConfiguration();

    boolean isDisabled();
}
