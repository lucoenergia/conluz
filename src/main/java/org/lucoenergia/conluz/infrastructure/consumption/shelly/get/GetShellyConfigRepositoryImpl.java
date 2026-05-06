package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.config.ShellyConfigEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class GetShellyConfigRepositoryImpl implements GetShellyConfigRepository {

    private final EntityManager entityManager;

    public GetShellyConfigRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ShellyConfig> getShellyConfig() {
        try {
            ShellyConfigEntity entity = (ShellyConfigEntity) entityManager
                    .createQuery("SELECT s FROM shelly_config s")
                    .getSingleResult();
            return Optional.of(toDomain(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    private ShellyConfig toDomain(ShellyConfigEntity entity) {
        return new ShellyConfig.Builder()
                .setId(entity.getId())
                .setEnabled(entity.getEnabled())
                .build();
    }
}
