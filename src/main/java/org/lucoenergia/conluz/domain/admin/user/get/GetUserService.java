package org.lucoenergia.conluz.domain.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.Set;
import java.util.UUID;

public interface GetUserService {

    PagedResult<User> findAll(PagedRequest pagedRequest);

    PagedResult<User> findAll(PagedRequest pagedRequest, Set<UUID> communityIds);

    User findById(UserId id);
}
