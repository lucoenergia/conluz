package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.SetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.config.ShellyConfigEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@Repository
@Transactional
public class SetShellyConfigRepositoryImpl implements SetShellyConfigRepository {

    private final EntityManager entityManager;

    public SetShellyConfigRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public ShellyConfig setShellyConfiguration(ShellyConfig config) {
        ShellyConfigEntity entity = new ShellyConfigEntity();
        entity.setId(config.getId());
        entity.setEnabled(config.getEnabled());
        ShellyConfigEntity saved = entityManager.merge(entity);
        return toDomain(saved);
    }

    private ShellyConfig toDomain(ShellyConfigEntity entity) {
        return new ShellyConfig.Builder()
                .setId(entity.getId())
                .setEnabled(entity.getEnabled())
                .build();
    }
}
