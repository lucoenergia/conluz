package org.lucoenergia.conluz.infrastructure.admin.user;

import org.lucoenergia.conluz.domain.admin.user.EnableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EnableUserController {

    private final EnableUserService service;

    public EnableUserController(EnableUserService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{id}/enable")
    public void enableUser(@PathVariable("id") String userId) {
        service.enable(new UserId(userId));
    }
}
