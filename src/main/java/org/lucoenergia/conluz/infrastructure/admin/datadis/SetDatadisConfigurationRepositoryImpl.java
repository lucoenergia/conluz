package org.lucoenergia.conluz.infrastructure.admin.datadis;

import org.lucoenergia.conluz.domain.admin.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.admin.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.DatadisConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SetDatadisConfigurationRepositoryImpl implements SetDatadisConfigurationRepository {

    private final DatadisConfigRepository datadisConfigRepository;

    public SetDatadisConfigurationRepositoryImpl(DatadisConfigRepository datadisConfigRepository) {
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

        datadisConfigRepository.save(newConfig);
        return new DatadisConfig(newConfig.getUsername(), newConfig.getPassword());
    }
}
