package org.lucoenergia.conluz.infrastructure.admin.supply;

import org.lucoenergia.conluz.domain.admin.supply.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
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
