package org.lucoenergia.conluz.infrastructure.admin.supply.enable;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.enable.EnableSupplyRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Transactional
@Repository
public class EnableSupplyRepositoryDatabase implements EnableSupplyRepository {

    private final SupplyRepository repository;
    private final SupplyEntityMapper mapper;

    public EnableSupplyRepositoryDatabase(SupplyRepository repository, SupplyEntityMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Supply enable(SupplyId id) {
        UUID supplyUuid = id.getId();
        Optional<SupplyEntity> result = repository.findById(supplyUuid);
        if (result.isEmpty()) {
            throw new SupplyNotFoundException(id);
        }
        SupplyEntity entity = result.get();
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            // idempotent: already enabled
            return mapper.map(entity);
        }
        entity.setEnabled(true);
        return mapper.map(repository.save(entity));
    }
}
