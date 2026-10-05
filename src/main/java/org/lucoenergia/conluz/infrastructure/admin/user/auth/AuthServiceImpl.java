package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.auth.*;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginAttempt;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.LoginFailureReason;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final AuthenticationThrottleService authenticationThrottleService;

    public AuthServiceImpl(Authenticator authenticator, GetUserRepository getUserRepository,
                           AuthRepository authRepository, BlacklistedTokenRepository blacklistedTokenRepository,
                           AuthenticationThrottleService authenticationThrottleService) {
        this.authenticator = authenticator;
        this.getUserRepository = getUserRepository;
        this.authRepository = authRepository;
        this.blacklistedTokenRepository = blacklistedTokenRepository;
        this.authenticationThrottleService = authenticationThrottleService;
    }

    @Override
    public Token login(Credentials credentials, String clientIp) {
        // Admitted before the password is checked, so a throttled attempt costs no BCrypt comparison, and closed
        // whatever the outcome, so the slot it holds is always released
        try (LoginAttempt attempt = authenticationThrottleService.startLogin(credentials.getUsername(), clientIp)) {
            try {
                authenticator.authenticate(credentials);
            } catch (DisabledException e) {
                attempt.failed(LoginFailureReason.DISABLED);
                throw e;
            } catch (BadCredentialsException e) {
                attempt.failed(LoginFailureReason.BAD_CREDENTIALS);
                throw e;
            }
            attempt.succeeded();
        }
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
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
