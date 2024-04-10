package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import org.lucoenergia.conluz.domain.production.huawei.get.GetHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.huawei.HuaweiConfig;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiConfigEntity;
import org.lucoenergia.conluz.infrastructure.production.huawei.HuaweiConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetHuaweiConfigRepositoryDatabase implements GetHuaweiConfigRepository {

    private final HuaweiConfigRepository huaweiConfigRepository;

    public GetHuaweiConfigRepositoryDatabase(HuaweiConfigRepository huaweiConfigRepository) {
        this.huaweiConfigRepository = huaweiConfigRepository;
    }

    @Override
    public Optional<HuaweiConfig> getHuaweiConfig() {
        Optional<HuaweiConfigEntity> entity = huaweiConfigRepository.findFirstByOrderByIdAsc();
        if (entity.isPresent()) {
            HuaweiConfigEntity configEntity = entity.get();
            return Optional.of(new HuaweiConfig.Builder()
                    .setUsername(configEntity.getUsername())
                    .setPassword(configEntity.getPassword())
                    .build());
        }
        return Optional.empty();
    }
}
