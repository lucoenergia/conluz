package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final Authenticator authenticator;
    private final GetUserRepository getUserRepository;
    private final AuthRepository authRepository;

    public AuthService(Authenticator authenticator, GetUserRepository getUserRepository, AuthRepository authRepository) {
        this.authenticator = authenticator;
        this.getUserRepository = getUserRepository;
        this.authRepository = authRepository;
    }

    public Token login(Credentials credentials) {
        authenticator.authenticate(credentials);
        Optional<User> user = getUserRepository.findById(UserId.of(credentials.getUsername()));
        if (user.isEmpty()) {
            throw new UserNotFoundException(UserId.of(credentials.getUsername()));
        }
        return authRepository.getToken(user.get());
    }
}
