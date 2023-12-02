package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserService;
import org.lucoenergia.conluz.infrastructure.admin.user.UserResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Add a new user
 */
@RestController
@RequestMapping("/api/v1/users")
public class CreateUserController {

    private final CreateUserService service;

    public CreateUserController(CreateUserService service) {
        this.service = service;
    }

    @PostMapping
    public UserResponse createUser(@RequestBody CreateUserBody body) {
        User user = service.create(body.getUser(), body.getPassword());
        return new UserResponse(user);
    }
}
