package org.lucoenergia.conluz.infrastructure.admin.user.login;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/login")
public class LoginUserController {


    @PostMapping
    public void login() {

    }
}
