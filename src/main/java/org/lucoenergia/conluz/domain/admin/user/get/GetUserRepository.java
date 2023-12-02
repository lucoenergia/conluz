package org.lucoenergia.conluz.domain.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.Optional;

public interface GetUserRepository {

    Optional<User> findById(UserId id);

    boolean existsById(UserId id);

    PagedResult<User> findAll(PagedRequest pagedRequest);
}
