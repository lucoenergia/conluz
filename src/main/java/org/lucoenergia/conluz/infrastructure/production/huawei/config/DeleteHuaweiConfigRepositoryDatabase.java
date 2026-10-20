package org.lucoenergia.conluz.infrastructure.production.huawei.config;

import org.lucoenergia.conluz.domain.production.huawei.config.DeleteHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class DeleteHuaweiConfigRepositoryDatabase implements DeleteHuaweiConfigRepository {

    private final HuaweiConfigRepository repository;

    public DeleteHuaweiConfigRepositoryDatabase(HuaweiConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void deleteByPlant(PlantId plantId) {
        repository.deleteByPlantId(plantId.getId());
    }
}
