package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@Service
public class GetSupplyServiceImpl implements GetSupplyService {

    private final GetSupplyRepository repository;
    private final AuthService authService;
    private final CommunityAccessGuard communityAccessGuard;

    public GetSupplyServiceImpl(GetSupplyRepository repository, AuthService authService,
                                CommunityAccessGuard communityAccessGuard) {
        this.repository = repository;
        this.authService = authService;
        this.communityAccessGuard = communityAccessGuard;
    }

    public PagedResult<Supply> findAll(PagedRequest pagedRequest) {
        return repository.findAll(pagedRequest);
    }

    @Override
    public Supply getById(SupplyId id) {
        Supply supply = repository.findById(id).orElseThrow(() -> new SupplyNotFoundException(id));

        if (!communityAccessGuard.canReadSupply(supply)) {
            throw new AccessDeniedException("You do not have permission to access this supply");
        }

        return supply;
    }

    @Override
    public List<Supply> getByUserId(UserId userId, UserId requestingUserId, boolean isAdmin) {
        // If user is ADMIN, return all supplies for the requested user
        if (isAdmin) {
            return repository.findByUserId(userId);
        }

        // Otherwise, only if the requesting user is the same as the requested user
        if (userId.getId().equals(requestingUserId.getId())) {
            return repository.findByUserId(userId);
        }

        throw new AccessDeniedException("You do not have permission to access supplies for this user");
    }
}
