package org.lucoenergia.conluz.domain.consumption.shelly.config;

import java.util.UUID;

public interface SetShellyConfigurationService {

    ShellyConfig setShellyConfiguration(UUID communityId, ShellyConfig config);
}
