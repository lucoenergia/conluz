package org.lucoenergia.conluz.domain.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GetUserRepository {

    long count();

    Optional<User> findByPersonalId(UserPersonalId id);

    Optional<User> findById(UserId id);

    boolean existsByPersonalId(UserPersonalId id);

    PagedResult<User> findAll(PagedRequest pagedRequest);

    PagedResult<User> findAllByCommunities(PagedRequest pagedRequest, Set<UUID> communityIds);

    List<User> findAll();

    Optional<User> getDefaultAdminUser();

    List<User> findAllUsersWithAtLeastOneSupply();
}
