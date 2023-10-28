package org.lucoenergia.conluz.infrastructure.admin;

import org.lucoenergia.conluz.domain.admin.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.production.SupplyEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetSupplyRepositoryImpl implements GetSupplyRepository {

    private final SupplyRepository supplyRepository;
    private final SupplyEntityMapper supplyEntityMapper;

    public GetSupplyRepositoryImpl(SupplyRepository supplyRepository, SupplyEntityMapper supplyEntityMapper) {
        this.supplyRepository = supplyRepository;
        this.supplyEntityMapper = supplyEntityMapper;
    }

    @Override
    public Optional<Supply> findById(SupplyId id) {
        Optional<SupplyEntity> supplyEntity = supplyRepository.findById(id.getId());

        if (supplyEntity.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(supplyEntityMapper.map(supplyEntity.get()));
    }
}
