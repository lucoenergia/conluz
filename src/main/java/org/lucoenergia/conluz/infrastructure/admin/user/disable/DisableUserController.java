package org.lucoenergia.conluz.infrastructure.admin.user.disable;

import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DisableUserController {

    private final DisableUserService service;

    public DisableUserController(DisableUserService service) {
        this.service = service;
    }

    @PostMapping(path = "/users/{id}/disable")
    public void disableUser(@PathVariable("id") UUID userId) {
        service.disable(UserId.of(userId));
    }
}
