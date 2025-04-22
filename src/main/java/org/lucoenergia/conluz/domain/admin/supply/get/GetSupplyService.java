package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
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
