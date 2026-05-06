package org.lucoenergia.conluz.infrastructure.consumption.shelly.config;

import org.lucoenergia.conluz.domain.consumption.shelly.config.SetShellyConfigurationService;
import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.SetShellyConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SetShellyConfigurationServiceImpl implements SetShellyConfigurationService {

    private final SetShellyConfigRepository setShellyConfigRepository;

    public SetShellyConfigurationServiceImpl(SetShellyConfigRepository setShellyConfigRepository) {
        this.setShellyConfigRepository = setShellyConfigRepository;
    }

    @Override
    public ShellyConfig setShellyConfiguration(ShellyConfig config) {
        return setShellyConfigRepository.setShellyConfiguration(config);
    }
}
