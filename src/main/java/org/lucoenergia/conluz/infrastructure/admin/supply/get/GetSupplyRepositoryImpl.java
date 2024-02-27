package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationRequestMapper;
import org.lucoenergia.conluz.infrastructure.shared.pagination.PaginationResultMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GetSupplyRepositoryImpl implements GetSupplyRepository {

    private final SupplyRepository supplyRepository;
    private final SupplyEntityMapper supplyEntityMapper;
    private final PaginationRequestMapper paginationRequestMapper;
    private final PaginationResultMapper<SupplyEntity, Supply> paginationResultMapper;

    public GetSupplyRepositoryImpl(SupplyRepository supplyRepository, SupplyEntityMapper supplyEntityMapper,
                                   PaginationRequestMapper paginationRequestMapper,
                                   PaginationResultMapper<SupplyEntity, Supply> paginationResultMapper) {
        this.supplyRepository = supplyRepository;
        this.supplyEntityMapper = supplyEntityMapper;
        this.paginationRequestMapper = paginationRequestMapper;
        this.paginationResultMapper = paginationResultMapper;
    }

    @Override
    public long count() {
        return supplyRepository.count();
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
        Page<SupplyEntity> result = supplyRepository.findAll(paginationRequestMapper.mapRequest(pagedRequest));
        return paginationResultMapper.mapResult(result, supplyEntityMapper.mapList(result.toList()));
    }
}
