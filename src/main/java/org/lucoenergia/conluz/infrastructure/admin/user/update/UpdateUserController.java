package org.lucoenergia.conluz.infrastructure.admin.user.update;

import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.user.create.UserResponse;
import org.springframework.web.bind.annotation.*;

/**
 * Updates an existing user
 */
@RestController
@RequestMapping("/api/v1")
public class UpdateUserController {

    private final UpdateUserAssembler assembler;
    private final UpdateUserService service;

    public UpdateUserController(UpdateUserAssembler assembler, UpdateUserService service) {
        this.assembler = assembler;
        this.service = service;
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable("id") String userId, @RequestBody UpdateUserBody body) {
        return new UserResponse(service.update(assembler.assemble(userId, body)));
    }
}
