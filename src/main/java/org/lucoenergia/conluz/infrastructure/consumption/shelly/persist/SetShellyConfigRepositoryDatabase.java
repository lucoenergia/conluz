package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.SetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.config.ShellyConfigEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class SetShellyConfigRepositoryDatabase implements SetShellyConfigRepository {

    private final ShellyConfigRepository shellyConfigRepository;

    public SetShellyConfigRepositoryDatabase(ShellyConfigRepository shellyConfigRepository) {
        this.shellyConfigRepository = shellyConfigRepository;
    }

    @Override
    public ShellyConfig setShellyConfiguration(ShellyConfig config) {

        ShellyConfigEntity newConfig;

        Optional<ShellyConfigEntity> optionalConfig = shellyConfigRepository.findFirstByOrderByIdAsc();
        if (optionalConfig.isEmpty()) {
            newConfig = new ShellyConfigEntity();
            newConfig.setId(UUID.randomUUID());
        } else {
            newConfig = optionalConfig.get();
        }

        newConfig.setEnabled(config.getEnabled());

        shellyConfigRepository.save(newConfig);

        return new ShellyConfig.Builder()
                .setId(newConfig.getId())
                .setEnabled(newConfig.getEnabled())
                .build();
    }
}
