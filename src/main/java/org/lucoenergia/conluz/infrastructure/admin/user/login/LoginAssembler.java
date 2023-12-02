package org.lucoenergia.conluz.infrastructure.admin.user.login;

import org.lucoenergia.conluz.domain.admin.user.auth.Credentials;
import org.lucoenergia.conluz.infrastructure.shared.web.Assembler;
import org.springframework.stereotype.Component;

@Component
public class LoginAssembler implements Assembler<LoginRequest, Credentials> {

    @Override
    public Credentials assemble(LoginRequest request) {
        return new Credentials(request.getUsername(), request.getPassword());
    }
}
