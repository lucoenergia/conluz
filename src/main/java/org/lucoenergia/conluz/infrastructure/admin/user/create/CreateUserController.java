package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.lucoenergia.conluz.domain.admin.user.CreateUserService;
import org.lucoenergia.conluz.domain.admin.user.User;
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

    private final CreateUserAssembler assembler;
    private final CreateUserService service;

    public CreateUserController(CreateUserAssembler assembler, CreateUserService service) {
        this.assembler = assembler;
        this.service = service;
    }

    @PostMapping
    public User createUser(@RequestBody CreateUserBody body) {
        return service.create(assembler.assemble(body), body.getPassword());
    }
}
