package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.infrastructure.production.huawei.config.HuaweiConfigEntity;
import org.lucoenergia.conluz.infrastructure.production.huawei.config.HuaweiConfigRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class GetHuaweiConfigRepositoryDatabase implements GetHuaweiConfigRepository {

    private final HuaweiConfigRepository huaweiConfigRepository;

    public GetHuaweiConfigRepositoryDatabase(HuaweiConfigRepository huaweiConfigRepository) {
        this.huaweiConfigRepository = huaweiConfigRepository;
    }

    @Override
    public Optional<HuaweiConfig> getHuaweiConfig(UUID plantId) {
        return huaweiConfigRepository.findByPlantId(plantId).map(this::toDomain);
    }

    @Override
    public List<HuaweiConfig> getEnabledHuaweiConfigs() {
        return huaweiConfigRepository.findAllByEnabledTrue().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private HuaweiConfig toDomain(HuaweiConfigEntity configEntity) {
        return new HuaweiConfig.Builder()
                .setId(configEntity.getId())
                .setUsername(configEntity.getUsername())
                .setPassword(configEntity.getPassword())
                .setBaseUrl(configEntity.getBaseUrl())
                .setEnabled(configEntity.getEnabled())
                .setPlantId(configEntity.getPlant() != null ? configEntity.getPlant().getId() : null)
                .build();
    }
}
