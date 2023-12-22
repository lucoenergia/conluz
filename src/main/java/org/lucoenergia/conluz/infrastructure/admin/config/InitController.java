package org.lucoenergia.conluz.infrastructure.admin.config;

import org.lucoenergia.conluz.domain.admin.config.init.InitService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/init")
public class InitController {

    private final InitService initService;

    public InitController(InitService initService) {
        this.initService = initService;
    }

    @PostMapping
    public void createUser(@RequestBody InitBody body) {
        initService.init(body.toDefaultAdminUserDomain());
    }
}
