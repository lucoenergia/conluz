package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;
import java.util.UUID;

public interface GetSupplyService {


    /**
     * Retrieves all supplies belonging to the given community. Intended for callers that administer
     * the community (platform or community admins).
     */
    PagedResult<Supply> findByCommunity(PagedRequest pagedRequest, UUID communityId);

    /**
     * Retrieves the supplies the given user owns within the given community. Intended for regular
     * members, who may only see their own supplies.
     */
    PagedResult<Supply> findByOwnerAndCommunity(PagedRequest pagedRequest, UserId ownerId, UUID communityId);

    Supply getById(SupplyId id);

    /**
     * Retrieves the supplies owned by a specified user, limited to the given scope.
     *
     * @param userId the identifier of the user whose supplies are to be retrieved
     * @param scope  which of those supplies may be returned, as decided by the access policy
     * @return the user's supplies that fall within the scope
     */
    List<Supply> getByUserId(UserId userId, SupplyOwnerScope scope);
}
