package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserService;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public PagedResult<UserResponse> getAllUsers(PagedRequest page) {
        PagedResult<User> users = service.findAll(page);

        List<UserResponse> responseUsers = users.getItems().stream()
                .map(UserResponse::new).toList();

        return new PagedResult<>(responseUsers, users.getSize(), users.getTotalElements(), users.getTotalPages(),
                users.getNumber());
    }
}
