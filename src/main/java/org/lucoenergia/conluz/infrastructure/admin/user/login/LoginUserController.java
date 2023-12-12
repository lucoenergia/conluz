package org.lucoenergia.conluz.infrastructure.admin.user.login;

import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/login")
public class LoginUserController {

    private final AuthService authService;
    private final LoginAssembler loginAssembler;

    public LoginUserController(AuthService authService, LoginAssembler loginAssembler) {
        this.authService = authService;
        this.loginAssembler = loginAssembler;
    }

    @PostMapping
    public ResponseEntity<Token> login(@RequestBody LoginRequest body) {
        return ResponseEntity.ok(authService.login(loginAssembler.assemble(body)));
    }
}
