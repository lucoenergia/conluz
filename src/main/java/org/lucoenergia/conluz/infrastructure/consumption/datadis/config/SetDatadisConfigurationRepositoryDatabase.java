package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SetDatadisConfigurationRepositoryDatabase implements SetDatadisConfigurationRepository {

    private final DatadisConfigRepository datadisConfigRepository;
    private final CommunityJpaRepository communityJpaRepository;

    public SetDatadisConfigurationRepositoryDatabase(DatadisConfigRepository datadisConfigRepository,
                                                     CommunityJpaRepository communityJpaRepository) {
        this.datadisConfigRepository = datadisConfigRepository;
        this.communityJpaRepository = communityJpaRepository;
    }

    @Override
    public DatadisConfig setDatadisConfiguration(DatadisConfig config) {
        DatadisConfigEntity entity = datadisConfigRepository.findAll().stream().findFirst()
                .orElseGet(() -> { DatadisConfigEntity e = new DatadisConfigEntity(); e.setId(UUID.randomUUID()); return e; });
        return persist(entity, config, null);
    }

    @Override
    public DatadisConfig setDatadisConfiguration(UUID communityId, DatadisConfig config) {
        DatadisConfigEntity entity = datadisConfigRepository.findByCommunityId(communityId)
                .orElseGet(() -> { DatadisConfigEntity e = new DatadisConfigEntity(); e.setId(UUID.randomUUID()); return e; });
        return persist(entity, config, communityId);
    }

    private DatadisConfig persist(DatadisConfigEntity entity, DatadisConfig config, UUID communityId) {
        entity.setUsername(config.getUsername());
        entity.setPassword(config.getPassword());
        entity.setBaseUrl(config.getBaseUrl());
        entity.setEnabled(config.getEnabled());
        if (communityId != null) {
            CommunityEntity community = communityJpaRepository.findById(communityId).orElse(null);
            entity.setCommunity(community);
        }
        datadisConfigRepository.save(entity);
        return new DatadisConfig.Builder()
                .setUsername(entity.getUsername())
                .setPassword(entity.getPassword())
                .setBaseUrl(entity.getBaseUrl())
                .setEnabled(entity.getEnabled())
                .setCommunityId(entity.getCommunity() != null ? entity.getCommunity().getId() : null)
                .build();
    }
}
