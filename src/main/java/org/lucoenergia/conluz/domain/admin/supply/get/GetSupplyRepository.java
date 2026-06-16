package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetSupplyRepository {

    long count();

    Optional<Supply> findById(SupplyId id);
    Optional<Supply> findByCode(SupplyCode code);

    PagedResult<Supply> findAll(PagedRequest pagedRequest);
    List<Supply> findAll();

    /**
     * All supplies belonging to the given community (paginated).
     */
    PagedResult<Supply> findByCommunity(PagedRequest pagedRequest, UUID communityId);

    /**
     * Supplies owned by {@code ownerId} that belong to the given community (paginated).
     */
    PagedResult<Supply> findByOwnerAndCommunity(PagedRequest pagedRequest, UserId ownerId, UUID communityId);

    List<Supply> findByUserId(UserId userId);

    List<Supply> findAllByCommunityId(UUID communityId);
}
