package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SetHuaweiConfigurationRepositoryDatabase implements SetHuaweiConfigurationRepository {

    private final HuaweiConfigRepository huaweiConfigRepository;
    private final PlantRepository plantRepository;

    public SetHuaweiConfigurationRepositoryDatabase(HuaweiConfigRepository huaweiConfigRepository,
                                                    PlantRepository plantRepository) {
        this.huaweiConfigRepository = huaweiConfigRepository;
        this.plantRepository = plantRepository;
    }

    @Override
    public HuaweiConfig setHuaweiConfiguration(HuaweiConfig config) {
        HuaweiConfigEntity newConfig = huaweiConfigRepository.findFirstBy()
                .orElseGet(() -> {
                    HuaweiConfigEntity e = new HuaweiConfigEntity();
                    e.setId(UUID.randomUUID());
                    return e;
                });

        applyConfig(newConfig, config);
        huaweiConfigRepository.save(newConfig);
        return toDomain(newConfig);
    }

    @Override
    public HuaweiConfig setHuaweiConfiguration(UUID plantId, HuaweiConfig config) {
        HuaweiConfigEntity newConfig = huaweiConfigRepository.findByPlantId(plantId)
                .orElseGet(() -> {
                    HuaweiConfigEntity e = new HuaweiConfigEntity();
                    e.setId(UUID.randomUUID());
                    PlantEntity plant = plantRepository.findById(plantId)
                            .orElseThrow(() -> new IllegalArgumentException("Plant not found: " + plantId));
                    e.setPlant(plant);
                    return e;
                });

        applyConfig(newConfig, config);
        huaweiConfigRepository.save(newConfig);
        return toDomain(newConfig);
    }

    private void applyConfig(HuaweiConfigEntity entity, HuaweiConfig config) {
        entity.setUsername(config.getUsername());
        entity.setPassword(config.getPassword());
        entity.setBaseUrl(config.getBaseUrl());
        entity.setEnabled(config.getEnabled());
    }

    private HuaweiConfig toDomain(HuaweiConfigEntity entity) {
        return new HuaweiConfig.Builder()
                .setId(entity.getId())
                .setUsername(entity.getUsername())
                .setPassword(entity.getPassword())
                .setBaseUrl(entity.getBaseUrl())
                .setEnabled(entity.getEnabled())
                .setPlantId(entity.getPlant() != null ? entity.getPlant().getId() : null)
                .build();
    }
}
