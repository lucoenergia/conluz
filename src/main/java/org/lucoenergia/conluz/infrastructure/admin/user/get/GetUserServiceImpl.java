package org.lucoenergia.conluz.infrastructure.admin.user.get;

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

@Transactional(readOnly = true)
@Service
public class GetUserServiceImpl implements GetUserService {

    private final GetUserRepository getUserRepository;

    public GetUserServiceImpl(GetUserRepository getUserRepository) {
        this.getUserRepository = getUserRepository;
    }

    @Override
    public PagedResult<User> findAll(PagedRequest pagedRequest) {

        // If not sorting is provided, sort by descendant order by default
        if (!pagedRequest.isSorted()) {
            final Order defaultOrder = new Order(Direction.ASC, "number");
            pagedRequest.addOrder(defaultOrder);
        }

        return getUserRepository.findAll(pagedRequest);
    }

    @Override
    public User findById(UserId id) {
        return getUserRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }
}
