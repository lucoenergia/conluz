package org.lucoenergia.conluz.domain.consumption.shelly.persist;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

import java.util.UUID;

public interface SetShellyConfigRepository {

    ShellyConfig setShellyConfiguration(UUID communityId, ShellyConfig config);
}
