package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class GetSupplyServiceImpl implements GetSupplyService {

    private final GetSupplyRepository repository;
    private final AuthService authService;

    public GetSupplyServiceImpl(GetSupplyRepository repository, AuthService authService) {
        this.repository = repository;
        this.authService = authService;
    }

    public PagedResult<Supply> findAll(PagedRequest pagedRequest) {
        return repository.findAll(pagedRequest);
    }

    @Override
    public Supply getById(SupplyId id) {
        Supply supply = repository.findById(id).orElseThrow(() -> new SupplyNotFoundException(id));

        Optional<User> user = authService.getCurrentUser();
        if (user.isEmpty()) {
            throw new AccessDeniedException("You do not have permission to access this supply");
        }

        // If user is ADMIN, return always
        if (user.get().getRole() == Role.ADMIN) {
            return supply;
        }
        // Otherwise, only if the supply belongs to the authenticated user
        if (supply.getUser() != null && supply.getUser().getId() != null && supply.getUser().getId().equals(user.get().getId())) {
            return supply;
        }

        throw new AccessDeniedException("You do not have permission to access this supply");
    }
}
