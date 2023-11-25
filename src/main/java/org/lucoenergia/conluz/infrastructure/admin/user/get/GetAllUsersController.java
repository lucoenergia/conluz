package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.GetUserService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Get all users
 */
@RestController
@RequestMapping("/api/v1/users")
public class GetAllUsersController {

    private final GetUserService service;

    public GetAllUsersController(GetUserService service) {
        this.service = service;
    }

    @GetMapping
    public PagedResult<User> getAllUsers(PagedRequest page) {
        return service.findAll(page);
    }
}
