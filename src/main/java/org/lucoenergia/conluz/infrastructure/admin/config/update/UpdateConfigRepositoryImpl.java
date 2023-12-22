package org.lucoenergia.conluz.infrastructure.admin.config.update;

import org.lucoenergia.conluz.domain.admin.config.init.update.UpdateConfigRepository;
import org.lucoenergia.conluz.infrastructure.admin.config.ConfigEntity;
import org.lucoenergia.conluz.infrastructure.admin.config.ConfigRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class UpdateConfigRepositoryImpl implements UpdateConfigRepository {

    private final ConfigRepository configRepository;

    public UpdateConfigRepositoryImpl(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public void markDefaultAdminUserAsInitialized() {
        ConfigEntity configEntity = configRepository.find();
        configEntity.markDefaultAdminUserAsInitialized();
        configRepository.save(configEntity);
    }
}
