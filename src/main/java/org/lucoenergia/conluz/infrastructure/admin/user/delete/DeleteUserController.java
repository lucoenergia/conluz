package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DeleteUserController {

    private final DeleteUserService service;

    public DeleteUserController(DeleteUserService service) {
        this.service = service;
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable("id") String userId) {
        service.delete(UserId.of(userId));
    }
}
