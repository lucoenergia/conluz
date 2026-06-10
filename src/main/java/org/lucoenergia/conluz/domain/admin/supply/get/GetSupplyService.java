package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface GetSupplyService {

    PagedResult<Supply> findAll(PagedRequest pagedRequest);

    /**
     * Retrieves the supplies visible to a non-platform-admin caller: supplies they own
     * unioned with supplies belonging to the communities they administer.
     *
     * @param pagedRequest      pagination/sorting request
     * @param ownerId           the id of the calling user (their owned supplies are included)
     * @param adminCommunityIds community ids the caller administers (may be empty)
     * @return the page of supplies visible to the caller
     */
    PagedResult<Supply> findAllVisible(PagedRequest pagedRequest, UserId ownerId, Set<UUID> adminCommunityIds);

    Supply getById(SupplyId id);

    /**
     * Retrieves a list of supplies associated with a specified user.
     *
     * @param userId the identifier of the user whose supplies are to be retrieved
     * @return a list of supplies associated with the specified user
     */
    List<Supply> getByUserId(UserId userId);
}
