package org.lucoenergia.conluz.infrastructure.admin.config.get;

import org.lucoenergia.conluz.domain.admin.config.init.get.GetConfigRepository;
import org.lucoenergia.conluz.infrastructure.admin.config.ConfigEntity;
import org.lucoenergia.conluz.infrastructure.admin.config.ConfigRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Repository
public class GetConfigRepositoryImpl implements GetConfigRepository {

    private final ConfigRepository configRepository;

    public GetConfigRepositoryImpl(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public boolean isDefaultAdminInitialized() {
        List<ConfigEntity> config = configRepository.findAll();
        if (config.isEmpty()) {
            return false;
        }
        return config.get(0).isDefaultAdminUserInitialized();
    }
}
