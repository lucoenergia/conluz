package org.lucoenergia.conluz.infrastructure.admin.user.password;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.PasswordChangeAttempt;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordService;
import org.lucoenergia.conluz.domain.admin.user.password.IncorrectCurrentPasswordException;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordUnchangedException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class ChangePasswordServiceImpl implements ChangePasswordService {

    private final GetUserRepository getUserRepository;
    private final ChangePasswordRepository changePasswordRepository;
    private final UserPasswordEncoder userPasswordEncoder;
    private final AuthService authService;
    private final AuthenticationThrottleService authenticationThrottleService;

    public ChangePasswordServiceImpl(GetUserRepository getUserRepository,
                                     ChangePasswordRepository changePasswordRepository,
                                     UserPasswordEncoder userPasswordEncoder,
                                     AuthService authService,
                                     AuthenticationThrottleService authenticationThrottleService) {
        this.getUserRepository = getUserRepository;
        this.changePasswordRepository = changePasswordRepository;
        this.userPasswordEncoder = userPasswordEncoder;
        this.authService = authService;
        this.authenticationThrottleService = authenticationThrottleService;
    }

    @Override
    public void changePassword(UserId userId, String currentPassword, String newPassword, Token usedToken,
                               String clientIp) {
        User user = getUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Admitted before the current password is checked, so a throttled attempt costs no BCrypt comparison, and
        // closed whatever the outcome, so the slot it holds is always released
        try (PasswordChangeAttempt attempt = authenticationThrottleService.startPasswordChange(user, clientIp)) {
            if (!userPasswordEncoder.matches(currentPassword, user.getPassword())) {
                attempt.failed();
                throw new IncorrectCurrentPasswordException();
            }

            // Whoever chose the current password still knows it, so setting it again changes nothing. Compared
            // exactly, with no trimming or normalisation. The current password was right, so the attempt neither
            // counts as a failure nor resets the counter
            if (currentPassword.equals(newPassword)) {
                throw new PasswordUnchangedException();
            }

            // A new password that breaks the policy is refused here: the current password was right, so the
            // attempt neither counts as a failure nor resets the counter
            String encodedPassword = userPasswordEncoder.encode(newPassword);
            changePasswordRepository.changePassword(userId, encodedPassword, Instant.now());

            authService.blacklistToken(usedToken);
            attempt.succeeded();
        }
    }
}
