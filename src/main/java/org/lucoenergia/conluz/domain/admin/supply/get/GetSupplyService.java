package org.lucoenergia.conluz.domain.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;

public interface GetSupplyService {

    PagedResult<Supply> findAll(PagedRequest pagedRequest);

    Supply getById(SupplyId id);

    /**
     * Retrieves a list of supplies associated with a specified user.
     *
     * @param userId the identifier of the user whose supplies are to be retrieved
     * @param requestingUserId the identifier of the user making the request
     * @param isAdmin a flag indicating if the requesting user has administrative privileges
     * @return a list of supplies associated with the specified user
     */
    List<Supply> getByUserId(UserId userId, UserId requestingUserId, boolean isAdmin);
}
