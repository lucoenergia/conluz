package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Service;

@Service
public class GetSupplyService {

    private final GetSupplyRepository repository;

    public GetSupplyService(GetSupplyRepository repository) {
        this.repository = repository;
    }

    public PagedResult<Supply> findAll(PagedRequest pagedRequest) {
        return repository.findAll(pagedRequest);
    }
}
