package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetSupplyRepositoryImpl implements GetSupplyRepository {

    private final SupplyRepository supplyRepository;
    private final SupplyEntityMapper supplyEntityMapper;
    private final PaginationMapper<SupplyEntity, Supply> paginationMapper;

    public GetSupplyRepositoryImpl(SupplyRepository supplyRepository, SupplyEntityMapper supplyEntityMapper,
                                   PaginationMapper<SupplyEntity, Supply> paginationMapper) {
        this.supplyRepository = supplyRepository;
        this.supplyEntityMapper = supplyEntityMapper;
        this.paginationMapper = paginationMapper;
    }

    @Override
    public Optional<Supply> findById(SupplyId id) {
        Optional<SupplyEntity> supplyEntity = supplyRepository.findById(id.getId());

        if (supplyEntity.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(supplyEntityMapper.map(supplyEntity.get()));
    }

    @Override
    public boolean existsById(SupplyId id) {
        return supplyRepository.existsById(id.getId());
    }

    @Override
    public PagedResult<Supply> findAll(PagedRequest pagedRequest) {
        Page<SupplyEntity> result = supplyRepository.findAll(paginationMapper.mapRequest(pagedRequest));
        return paginationMapper.mapResult(result, supplyEntityMapper.mapList(result.toList()));
    }
}
