package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import org.lucoenergia.conluz.domain.admin.user.auth.Authenticator;
import org.lucoenergia.conluz.domain.admin.user.auth.Credentials;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatorImpl implements Authenticator {

    private final AuthenticationManager authenticationManager;

    public AuthenticatorImpl(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public void authenticate(Credentials credentials) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(credentials.getUsername(),
                credentials.getPassword()));
    }
}
