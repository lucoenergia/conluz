package org.lucoenergia.conluz.infrastructure.consumption.datadis.config;

import org.lucoenergia.conluz.domain.consumption.datadis.config.DatadisConfig;
import org.lucoenergia.conluz.domain.consumption.datadis.config.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SetDatadisConfigurationRepositoryDatabase implements SetDatadisConfigurationRepository {

    private final DatadisConfigRepository datadisConfigRepository;

    public SetDatadisConfigurationRepositoryDatabase(DatadisConfigRepository datadisConfigRepository) {
        this.datadisConfigRepository = datadisConfigRepository;
    }

    @Override
    public DatadisConfig setDatadisConfiguration(DatadisConfig config) {
        DatadisConfigEntity newConfig;

        Optional<DatadisConfigEntity> optionalConfig = datadisConfigRepository.findFirstByOrderByIdAsc();
        if (optionalConfig.isEmpty()) {
            newConfig = new DatadisConfigEntity();
            newConfig.setId(UUID.randomUUID());
        } else {
            newConfig = optionalConfig.get();
        }

        newConfig.setUsername(config.getUsername());
        newConfig.setPassword(config.getPassword());
        newConfig.setBaseUrl(config.getBaseUrl());
        newConfig.setEnabled(config.getEnabled());

        datadisConfigRepository.save(newConfig);

        return new DatadisConfig.Builder()
                .setUsername(newConfig.getUsername())
                .setPassword(newConfig.getPassword())
                .setBaseUrl(newConfig.getBaseUrl())
                .setEnabled(newConfig.getEnabled())
                .build();
    }
}
