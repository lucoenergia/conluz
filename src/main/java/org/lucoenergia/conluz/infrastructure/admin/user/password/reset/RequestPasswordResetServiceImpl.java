package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.AuthenticationThrottleService;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.password.reset.PasswordResetRequestOutcome;
import org.lucoenergia.conluz.domain.admin.user.password.reset.RequestPasswordResetService;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@Transactional
public class RequestPasswordResetServiceImpl implements RequestPasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestPasswordResetServiceImpl.class);

    private final AuthenticationThrottleService authenticationThrottleService;
    private final GetUserRepository getUserRepository;
    private final LockUserRepository lockUserRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final CreateOneTimeTokenService createOneTimeTokenService;
    private final EmailSender emailSender;
    private final PasswordResetEmailFactory emailFactory;
    private final PasswordResetLink link;
    private final Clock clock;

    public RequestPasswordResetServiceImpl(AuthenticationThrottleService authenticationThrottleService,
                                           GetUserRepository getUserRepository,
                                           LockUserRepository lockUserRepository,
                                           GetOneTimeTokenService getOneTimeTokenService,
                                           CreateOneTimeTokenService createOneTimeTokenService,
                                           EmailSender emailSender,
                                           PasswordResetEmailFactory emailFactory,
                                           PasswordResetLink link,
                                           Clock clock) {
        this.authenticationThrottleService = authenticationThrottleService;
        this.getUserRepository = getUserRepository;
        this.lockUserRepository = lockUserRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.createOneTimeTokenService = createOneTimeTokenService;
        this.emailSender = emailSender;
        this.emailFactory = emailFactory;
        this.link = link;
        this.clock = clock;
    }

    @Override
    public void request(String personalId, String clientIp) {
        authenticationThrottleService.countPasswordResetRequest(clientIp);

        PasswordResetRequestOutcome outcome = send(personalId);

        LOGGER.info("Password reset requested: account={}, ip={}, outcome={}",
                UserPersonalId.mask(UserPersonalId.normalize(personalId)), clientIp, outcome);
    }

    private PasswordResetRequestOutcome send(String personalId) {
        Optional<User> found = getUserRepository.findByPersonalId(UserPersonalId.of(personalId));
        if (found.isEmpty()) {
            return PasswordResetRequestOutcome.UNKNOWN;
        }
        User user = found.get();
        if (!user.isEnabled()) {
            return PasswordResetRequestOutcome.DISABLED;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return PasswordResetRequestOutcome.NO_EMAIL;
        }
        if (!link.isConfigured()) {
            // No token is issued: the user's earlier link keeps working, and the daily limit is not used up
            LOGGER.warn("Email {} not sent: the public web URL is not configured", PasswordResetEmailFactory.CATEGORY);
            return PasswordResetRequestOutcome.FAILED;
        }

        UserId userId = UserId.of(user.getId());
        // The user row is locked before counting, so concurrent requests for the same user are counted one after
        // the other and the daily limit is exact. Issuing locks it again, which is a no-op within this transaction.
        if (!lockUserRepository.lock(userId)) {
            return PasswordResetRequestOutcome.UNKNOWN;
        }
        long issued = getOneTimeTokenService.countIssuedSince(userId, OneTimeTokenPurpose.PASSWORD_RESET,
                clock.instant().minus(DAILY_WINDOW));
        if (issued >= DAILY_LIMIT) {
            return PasswordResetRequestOutcome.OVER_LIMIT;
        }
        RawOneTimeToken token = createOneTimeTokenService.issue(userId, OneTimeTokenPurpose.PASSWORD_RESET);
        // Handed over only once this transaction commits
        emailSender.send(emailFactory.build(user.getEmail(), link.forToken(token).orElseThrow()));
        return PasswordResetRequestOutcome.SENT;
    }
}
