package org.lucoenergia.conluz.domain.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

public interface GetUserService {

    PagedResult<User> findAll(PagedRequest pagedRequest);

    /**
     * Retrieves the users within the given scope, as decided by the access policy, each with every one
     * of their memberships attached. Bounding which memberships a response may show is left to the
     * response, after anything that decides on the full memberships has run.
     */
    PagedResult<User> findAllVisible(PagedRequest pagedRequest, UserScope scope);

    User findById(UserId id);
}
