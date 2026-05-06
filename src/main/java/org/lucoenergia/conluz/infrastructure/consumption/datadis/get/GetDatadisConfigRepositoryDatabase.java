package org.lucoenergia.conluz.infrastructure.consumption.datadis.get;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetDatadisConfigRepositoryDatabase implements GetDatadisConfigRepository {

    private final DatadisConfigRepository datadisConfigRepository;

    public GetDatadisConfigRepositoryDatabase(DatadisConfigRepository datadisConfigRepository) {
        this.datadisConfigRepository = datadisConfigRepository;
    }

    @Override
    public Optional<DatadisConfig> getDatadisConfig() {
        Optional<DatadisConfigEntity> entity = datadisConfigRepository.findFirstByOrderByIdAsc();
        if (entity.isPresent()) {
            DatadisConfigEntity configEntity = entity.get();
            return Optional.of(new DatadisConfig.Builder()
                    .setUsername(configEntity.getUsername())
                    .setPassword(configEntity.getPassword())
                    .setBaseUrl(configEntity.getBaseUrl())
                    .setEnabled(configEntity.getEnabled())
                    .build());
        }
        return Optional.empty();
    }
}
