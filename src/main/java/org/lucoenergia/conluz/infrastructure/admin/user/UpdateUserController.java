package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.UpdateUserService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.springframework.web.bind.annotation.*;

/**
 * Updates an existing user
 */
@RestController
@RequestMapping("/api/v1/users")
public class UpdateUserController {

    private final UpdateUserAssembler assembler;
    private final UpdateUserService service;

    public UpdateUserController(UpdateUserAssembler assembler, UpdateUserService service) {
        this.assembler = assembler;
        this.service = service;
    }

    @PutMapping
    public User updateUser(@RequestParam("id") String userId, @RequestBody UpdateUserBody body) {
        return service.update(assembler.assemble(userId, body));
    }
}
