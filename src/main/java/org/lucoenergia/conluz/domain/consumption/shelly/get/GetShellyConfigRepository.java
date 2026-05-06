package org.lucoenergia.conluz.domain.consumption.shelly.get;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

import java.util.Optional;

public interface GetShellyConfigRepository {

    Optional<ShellyConfig> getShellyConfig();
}
