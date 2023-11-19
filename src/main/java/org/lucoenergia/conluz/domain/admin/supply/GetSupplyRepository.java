package org.lucoenergia.conluz.domain.admin.supply;

import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.Optional;

public interface GetSupplyRepository {

    Optional<Supply> findById(SupplyId id);

    boolean existsById(SupplyId id);

    PagedResult<Supply> findAll(PagedRequest pagedRequest);
}
