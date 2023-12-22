package org.lucoenergia.conluz.infrastructure.admin.config.get;

import org.lucoenergia.conluz.domain.admin.config.init.get.GetConfigRepository;
import org.lucoenergia.conluz.infrastructure.admin.config.ConfigEntity;
import org.lucoenergia.conluz.infrastructure.admin.config.ConfigRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Transactional
@Repository
public class GetConfigRepositoryImpl implements GetConfigRepository {

    private final ConfigRepository configRepository;

    public GetConfigRepositoryImpl(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public boolean isDefaultAdminInitialized() {
        Optional<ConfigEntity> config = configRepository.find();
        return config
                .map(ConfigEntity::isDefaultAdminUserInitialized)
                .orElse(false);
    }
}
