package org.lucoenergia.conluz.domain.consumption.shelly.persist;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;

public interface SetShellyConfigRepository {

    ShellyConfig setShellyConfiguration(ShellyConfig config);
}
