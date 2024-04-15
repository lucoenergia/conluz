package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.domain.production.huawei.config.SetHuaweiConfigurationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SetHuaweiConfigurationRepositoryDatabase implements SetHuaweiConfigurationRepository {

    private final HuaweiConfigRepository huaweiConfigRepository;

    public SetHuaweiConfigurationRepositoryDatabase(HuaweiConfigRepository huaweiConfigRepository) {
        this.huaweiConfigRepository = huaweiConfigRepository;
    }

    @Override
    public HuaweiConfig setHuaweiConfiguration(HuaweiConfig config) {
        HuaweiConfigEntity newConfig;

        Optional<HuaweiConfigEntity> optionalConfig = huaweiConfigRepository.findFirstByOrderByIdAsc();
        if (optionalConfig.isEmpty()) {
            newConfig = new HuaweiConfigEntity();
            newConfig.setId(UUID.randomUUID());
        } else {
            newConfig = optionalConfig.get();
        }

        newConfig.setUsername(config.getUsername());
        newConfig.setPassword(config.getPassword());

        huaweiConfigRepository.save(newConfig);
        return new HuaweiConfig.Builder()
                .setUsername(newConfig.getUsername())
                .setPassword(newConfig.getPassword())
                .build();
    }
}
