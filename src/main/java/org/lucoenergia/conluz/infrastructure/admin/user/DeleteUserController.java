package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.DeleteUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class DeleteUserController {

    private final DeleteUserService service;

    public DeleteUserController(DeleteUserService service) {
        this.service = service;
    }

    @DeleteMapping
    public void deleteUser(@RequestParam("id") String userId) {
        service.delete(new UserId(userId));
    }
}
