package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.Optional;

public interface GetSupplyRepository {

    long count();

    Optional<Supply> findById(SupplyId id);

    boolean existsById(SupplyId id);

    PagedResult<Supply> findAll(PagedRequest pagedRequest);
}
