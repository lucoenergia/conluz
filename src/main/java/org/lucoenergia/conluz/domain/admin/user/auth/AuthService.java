package org.lucoenergia.conluz.domain.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional(readOnly = true)
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
        Optional<User> user = getUserRepository.findByPersonalId(UserPersonalId.of(credentials.getUsername()));
        if (user.isEmpty()) {
            throw new UserNotFoundException();
        }
        return authRepository.getToken(user.get());
    }
}
