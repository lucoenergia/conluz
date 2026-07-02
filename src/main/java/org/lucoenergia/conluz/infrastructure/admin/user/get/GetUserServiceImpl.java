package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Transactional(readOnly = true)
@Service
public class GetUserServiceImpl implements GetUserService {

    private final GetUserRepository getUserRepository;
    private final GetMembershipsRepository getMembershipsRepository;

    public GetUserServiceImpl(GetUserRepository getUserRepository,
                              GetMembershipsRepository getMembershipsRepository) {
        this.getUserRepository = getUserRepository;
        this.getMembershipsRepository = getMembershipsRepository;
    }

    @Override
    public PagedResult<User> findAll(PagedRequest pagedRequest) {
        if (!pagedRequest.isSorted()) {
            final Order defaultOrder = new Order(Direction.ASC, "number");
            pagedRequest.addOrder(defaultOrder);
        }
        return withMemberships(getUserRepository.findAll(pagedRequest));
    }

    @Override
    public PagedResult<User> findAllByCommunities(PagedRequest pagedRequest, Set<UUID> communityIds) {
        if (communityIds == null) {
            return findAll(pagedRequest);
        }
        if (!pagedRequest.isSorted()) {
            final Order defaultOrder = new Order(Direction.ASC, "number");
            pagedRequest.addOrder(defaultOrder);
        }
        return withMemberships(getUserRepository.findAllByCommunities(pagedRequest, communityIds));
    }

    @Override
    public User findById(UserId id) {
        User user = getUserRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.setMemberships(getMembershipsRepository.findByUserId(id.getId()));
        return user;
    }

    /**
     * Enriches a page of users with their community memberships using a single batch query,
     * avoiding N+1 lookups. Users without memberships get an empty list.
     */
    private PagedResult<User> withMemberships(PagedResult<User> users) {
        List<UUID> userIds = users.getItems().stream()
                .map(User::getId)
                .toList();
        Map<UUID, List<CommunityMembership>> membershipsByUser = getMembershipsRepository.findByUserIds(userIds);
        users.getItems().forEach(user ->
                user.setMemberships(membershipsByUser.getOrDefault(user.getId(), List.of())));
        return users;
    }
}
