package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.PasswordResetAttempt;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordUnchangedException;
import org.lucoenergia.conluz.domain.admin.user.password.reset.PasswordResetTokenInvalidException;
import org.lucoenergia.conluz.domain.admin.user.password.reset.ResetPasswordService;
import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional
public class ResetPasswordServiceImpl implements ResetPasswordService {

    private final AuthenticationThrottleService authenticationThrottleService;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final ConsumeOneTimeTokenService consumeOneTimeTokenService;
    private final LockUserRepository lockUserRepository;
    private final GetUserRepository getUserRepository;
    private final ChangePasswordRepository changePasswordRepository;
    private final UserPasswordEncoder userPasswordEncoder;

    public ResetPasswordServiceImpl(AuthenticationThrottleService authenticationThrottleService,
                                    GetOneTimeTokenService getOneTimeTokenService,
                                    ConsumeOneTimeTokenService consumeOneTimeTokenService,
                                    LockUserRepository lockUserRepository,
                                    GetUserRepository getUserRepository,
                                    ChangePasswordRepository changePasswordRepository,
                                    UserPasswordEncoder userPasswordEncoder) {
        this.authenticationThrottleService = authenticationThrottleService;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.consumeOneTimeTokenService = consumeOneTimeTokenService;
        this.lockUserRepository = lockUserRepository;
        this.getUserRepository = getUserRepository;
        this.changePasswordRepository = changePasswordRepository;
        this.userPasswordEncoder = userPasswordEncoder;
    }

    @Override
    public void reset(String rawToken, String newPassword, String clientIp) {
        try (PasswordResetAttempt attempt = authenticationThrottleService.startPasswordReset(clientIp)) {
            // The lock order of ConsumeOneTimeTokenService: find the owner without locking, lock the user row, and
            // only then consume, which locks the token row
            Optional<UserId> owner = getOneTimeTokenService.findOwner(rawToken, OneTimeTokenPurpose.PASSWORD_RESET);
            if (owner.isEmpty()) {
                throw invalid(attempt);
            }
            UserId userId = owner.get();
            if (!lockUserRepository.lock(userId)) {
                throw invalid(attempt);
            }
            // Read after the lock, so a user disabled meanwhile is seen as disabled
            Optional<User> found = getUserRepository.findById(userId);
            if (found.isEmpty() || !found.get().isEnabled()) {
                throw invalid(attempt);
            }
            User user = found.get();

            // Neither refusal consumes the token, so the user can try again with the same link
            PasswordPolicy.check(newPassword);
            if (userPasswordEncoder.matches(newPassword, user.getPassword())) {
                throw new PasswordUnchangedException();
            }

            // The token may have been used or revoked since it was found
            Optional<UserId> consumedBy = consumeOneTimeTokenService.consume(rawToken,
                    OneTimeTokenPurpose.PASSWORD_RESET);
            if (consumedBy.isEmpty() || !consumedBy.get().equals(userId)) {
                throw invalid(attempt);
            }

            // Two clocks meet here. The token's times came from the application Clock, but password_changed_at is
            // compared with the issue time of session tokens, which come from the JVM clock: it must come from the
            // same one, or the earlier sessions would not be ended.
            changePasswordRepository.changePassword(userId, userPasswordEncoder.encode(newPassword), Instant.now());

            attempt.succeeded(user);
        }
    }

    private static PasswordResetTokenInvalidException invalid(PasswordResetAttempt attempt) {
        attempt.failed();
        return new PasswordResetTokenInvalidException();
    }
}
