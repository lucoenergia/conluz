package org.lucoenergia.conluz.infrastructure.consumption.shelly.persist;

import org.lucoenergia.conluz.domain.consumption.shelly.config.ShellyConfig;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.SetShellyConfigRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
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
    private final CommunityJpaRepository communityJpaRepository;

    public SetShellyConfigRepositoryDatabase(ShellyConfigRepository shellyConfigRepository,
                                             CommunityJpaRepository communityJpaRepository) {
        this.shellyConfigRepository = shellyConfigRepository;
        this.communityJpaRepository = communityJpaRepository;
    }

    @Override
    public ShellyConfig setShellyConfiguration(ShellyConfig config) {
        ShellyConfigEntity entity = shellyConfigRepository.findAll().stream().findFirst()
                .orElseGet(() -> { ShellyConfigEntity e = new ShellyConfigEntity(); e.setId(UUID.randomUUID()); return e; });
        return persist(entity, config, null);
    }

    @Override
    public ShellyConfig setShellyConfiguration(UUID communityId, ShellyConfig config) {
        ShellyConfigEntity entity = shellyConfigRepository.findByCommunityId(communityId)
                .orElseGet(() -> { ShellyConfigEntity e = new ShellyConfigEntity(); e.setId(UUID.randomUUID()); return e; });
        return persist(entity, config, communityId);
    }

    private ShellyConfig persist(ShellyConfigEntity entity, ShellyConfig config, UUID communityId) {
        entity.setEnabled(config.getEnabled());
        if (communityId != null) {
            CommunityEntity community = communityJpaRepository.findById(communityId).orElse(null);
            entity.setCommunity(community);
        }
        shellyConfigRepository.save(entity);
        return new ShellyConfig.Builder()
                .setId(entity.getId())
                .setEnabled(entity.getEnabled())
                .setCommunityId(entity.getCommunity() != null ? entity.getCommunity().getId() : null)
                .build();
    }
}
