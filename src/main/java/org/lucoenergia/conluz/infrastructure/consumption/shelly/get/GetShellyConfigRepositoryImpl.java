package org.lucoenergia.conluz.infrastructure.consumption.shelly.get;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.ShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.shelly.config.ShellyConfigEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional(readOnly = true)
public class GetShellyConfigRepositoryImpl implements GetShellyConfigRepository {

    private final ShellyConfigRepository shellyConfigRepository;

    public GetShellyConfigRepositoryImpl(ShellyConfigRepository shellyConfigRepository) {
        this.shellyConfigRepository = shellyConfigRepository;
    }

    @Override
    public Optional<ShellyConfig> getShellyConfig() {
        return shellyConfigRepository.findFirstBy().map(this::toDomain);
    }

    @Override
    public Optional<ShellyConfig> getShellyConfig(UUID communityId) {
        return shellyConfigRepository.findByCommunityId(communityId).map(this::toDomain);
    }

    private ShellyConfig toDomain(ShellyConfigEntity entity) {
        return new ShellyConfig.Builder()
                .setId(entity.getId())
                .setEnabled(entity.getEnabled())
                .setCommunityId(entity.getCommunity() != null ? entity.getCommunity().getId() : null)
                .build();
    }
}
