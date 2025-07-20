package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.auth.*;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class AuthServiceImpl implements AuthService {

    private final Authenticator authenticator;
    private final GetUserRepository getUserRepository;
    private final AuthRepository authRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    public AuthServiceImpl(Authenticator authenticator, GetUserRepository getUserRepository,
                           AuthRepository authRepository, BlacklistedTokenRepository blacklistedTokenRepository) {
        this.authenticator = authenticator;
        this.getUserRepository = getUserRepository;
        this.authRepository = authRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
    }

    @Override
    public Token login(Credentials credentials) {
        authenticator.authenticate(credentials);
        Optional<User> user = getUserRepository.findByPersonalId(UserPersonalId.of(credentials.getUsername()));
        if (user.isEmpty()) {
            throw new UserNotFoundException();
        }
        return authRepository.getToken(user.get());
    }

    @Override
    public void logout() {
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional
    public boolean blacklistToken(Token token) {
        Optional<String> jti = authRepository.getJtiFromToken(token);
        Date expirationDate = authRepository.getExpirationDate(token);

        if (jti.isPresent() && expirationDate != null) {
            Instant expiration = expirationDate.toInstant();
            blacklistedTokenRepository.save(new BlacklistedToken(jti.get(), expiration));
            return true;
        }

        return false;
    }

    @Override
    public User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
