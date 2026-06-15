package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetSupplyServiceImpl implements GetSupplyService {

    private final GetSupplyRepository repository;

    public GetSupplyServiceImpl(GetSupplyRepository repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<Supply> findAll(PagedRequest pagedRequest) {
        return repository.findAll(pagedRequest);
    }

    @Override
    public PagedResult<Supply> findAllVisible(PagedRequest pagedRequest, UserId ownerId, Set<UUID> adminCommunityIds) {
        return repository.findByOwnerOrCommunities(pagedRequest, ownerId, adminCommunityIds);
    }

    @Override
    public PagedResult<Supply> findByCommunity(PagedRequest pagedRequest, UUID communityId) {
        return repository.findByCommunity(pagedRequest, communityId);
    }

    @Override
    public PagedResult<Supply> findByOwnerAndCommunity(PagedRequest pagedRequest, UserId ownerId, UUID communityId) {
        return repository.findByOwnerAndCommunity(pagedRequest, ownerId, communityId);
    }

    @Override
    public Supply getById(SupplyId id) {
        return repository.findById(id).orElseThrow(() -> new SupplyNotFoundException(id));
    }

    @Override
    public List<Supply> getByUserId(UserId userId) {
        return repository.findByUserId(userId);
    }
}
