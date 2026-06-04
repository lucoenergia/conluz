package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class GetDatadisConfigRepositoryDatabase implements GetDatadisConfigRepository {

    private final DatadisConfigRepository datadisConfigRepository;

    public GetDatadisConfigRepositoryDatabase(DatadisConfigRepository datadisConfigRepository) {
        this.datadisConfigRepository = datadisConfigRepository;
    }

    @Override
    public Optional<DatadisConfig> getDatadisConfig() {
        return datadisConfigRepository.findFirstBy().map(this::toDomain);
    }

    @Override
    public Optional<DatadisConfig> findByCommunityId(UUID communityId) {
        Optional<DatadisConfigEntity> entity = datadisConfigRepository.findByCommunityId(communityId);
        return entity.map(this::toDomain);
    }

    @Override
    public List<DatadisConfig> findAllEnabled() {
        return datadisConfigRepository.findAllByEnabledTrue().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private DatadisConfig toDomain(DatadisConfigEntity configEntity) {
        return new DatadisConfig.Builder()
                .setUsername(configEntity.getUsername())
                .setPassword(configEntity.getPassword())
                .setBaseUrl(configEntity.getBaseUrl())
                .setEnabled(configEntity.getEnabled())
                .setCommunityId(configEntity.getCommunity() != null ? configEntity.getCommunity().getId() : null)
                .build();
    }
}
