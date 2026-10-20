package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationLoginRequiredException;
import org.lucoenergia.conluz.domain.admin.community.membership.accept.InvitationTokenInvalidException;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.throttle.InvitationAttempt;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.password.ChangePasswordRepository;
import org.lucoenergia.conluz.domain.admin.user.password.PasswordPolicy;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.LockPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.UpdatePlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.token.ConsumeOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.update.UpdateUserEmailRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;
import org.lucoenergia.conluz.infrastructure.admin.user.password.UserPasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Accepts a platform-admin invitation with its link and its confirmation code (ADR-0004 §4, §5), in one transaction
 * under the lock order user row, invitation row, token rows.
 *
 * <p>A missing or wrong code is not thrown: it is answered as an {@link Outcome}, so that the count of failed
 * attempts, and the revocation of the link at the fifth one, are committed with this transaction. The caller, which
 * runs outside any transaction, turns the outcome into the error answered. Every other refusal writes nothing, and is
 * thrown.</p>
 */
@Component
@Transactional
public class PlatformAdminInvitationAcceptance {

    /**
     * The failed code attempts after which the invitation's link is revoked (ADR-0004 §4).
     */
    public static final int MAX_FAILED_CODE_ATTEMPTS = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformAdminInvitationAcceptance.class);
    private static final OneTimeTokenPurpose PURPOSE = OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION;

    private final GetOneTimeTokenService getOneTimeTokenService;
    private final ConsumeOneTimeTokenService consumeOneTimeTokenService;
    private final RevokeOneTimeTokenService revokeOneTimeTokenService;
    private final LockUserRepository lockUserRepository;
    private final LockPlatformAdminInvitationRepository lockInvitationRepository;
    private final GetUserRepository getUserRepository;
    private final GetPlatformAdminInvitationRepository getInvitationRepository;
    private final UpdatePlatformAdminInvitationRepository updateInvitationRepository;
    private final ManagePlatformAdminRepository managePlatformAdminRepository;
    private final ChangePasswordRepository changePasswordRepository;
    private final UpdateUserEmailRepository updateUserEmailRepository;
    private final UserPasswordEncoder userPasswordEncoder;
    private final Clock clock;

    public PlatformAdminInvitationAcceptance(GetOneTimeTokenService getOneTimeTokenService,
                                             ConsumeOneTimeTokenService consumeOneTimeTokenService,
                                             RevokeOneTimeTokenService revokeOneTimeTokenService,
                                             LockUserRepository lockUserRepository,
                                             LockPlatformAdminInvitationRepository lockInvitationRepository,
                                             GetUserRepository getUserRepository,
                                             GetPlatformAdminInvitationRepository getInvitationRepository,
                                             UpdatePlatformAdminInvitationRepository updateInvitationRepository,
                                             ManagePlatformAdminRepository managePlatformAdminRepository,
                                             ChangePasswordRepository changePasswordRepository,
                                             UpdateUserEmailRepository updateUserEmailRepository,
                                             UserPasswordEncoder userPasswordEncoder,
                                             Clock clock) {
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.consumeOneTimeTokenService = consumeOneTimeTokenService;
        this.revokeOneTimeTokenService = revokeOneTimeTokenService;
        this.lockUserRepository = lockUserRepository;
        this.lockInvitationRepository = lockInvitationRepository;
        this.getUserRepository = getUserRepository;
        this.getInvitationRepository = getInvitationRepository;
        this.updateInvitationRepository = updateInvitationRepository;
        this.managePlatformAdminRepository = managePlatformAdminRepository;
        this.changePasswordRepository = changePasswordRepository;
        this.updateUserEmailRepository = updateUserEmailRepository;
        this.userPasswordEncoder = userPasswordEncoder;
        this.clock = clock;
    }

    /**
     * How an acceptance that got as far as the confirmation code ended.
     */
    public enum Outcome {
        ACCEPTED,
        /**
         * The code was missing or wrong; the failed attempt is counted and nothing else changed.
         */
        WRONG_CODE,
        /**
         * The code was missing or wrong for the fifth time; the invitation's link is revoked.
         */
        ATTEMPTS_EXHAUSTED
    }

    /**
     * The public path: an account without a password chooses its first password. The checks of community invitations
     * come first, unchanged and not counted (MEM-016): an account with a password is told to log in, and a password
     * that breaks the policy is refused, both before the code is looked at.
     */
    public Outcome acceptWithNewPassword(String rawToken, String confirmationCode, String newPassword,
                                         InvitationAttempt attempt) {
        Usable invitation = lockAndFind(rawToken, attempt);
        if (invitation.user().getPassword() != null) {
            throw new InvitationLoginRequiredException();
        }
        PasswordPolicy.check(newPassword);

        Optional<Outcome> refused = checkCode(invitation, confirmationCode, attempt);
        if (refused.isPresent()) {
            return refused.get();
        }

        accept(rawToken, invitation, attempt);
        changePasswordRepository.changePassword(invitation.token().userId(), userPasswordEncoder.encode(newPassword),
                clock.instant());
        log(invitation, "public");
        return Outcome.ACCEPTED;
    }

    /**
     * The authenticated path: logging in proves the person, so the token may only be the caller's own invitation.
     */
    public Outcome acceptAsUser(UserId caller, String rawToken, String confirmationCode, InvitationAttempt attempt) {
        Usable invitation = lockAndFind(rawToken, attempt);
        if (!invitation.token().userId().equals(caller)) {
            throw invalid(attempt);
        }

        Optional<Outcome> refused = checkCode(invitation, confirmationCode, attempt);
        if (refused.isPresent()) {
            return refused.get();
        }

        accept(rawToken, invitation, attempt);
        log(invitation, "authenticated");
        return Outcome.ACCEPTED;
    }

    /**
     * Finds the token's invitation without locking, locks the user row and the invitation row, then checks again,
     * under the locks, that the token is still valid for that invitation and the invitation is still pending: an
     * acceptance or a resend that held the locks first is seen as such. A disabled account is not refused: it is
     * granted the role and still cannot log in (ADR-0004, "What the observable events now mean").
     */
    private Usable lockAndFind(String rawToken, InvitationAttempt attempt) {
        OneTimeTokenPlatformAdminInvitation token = getOneTimeTokenService.findPlatformAdminInvitation(rawToken,
                PURPOSE).orElseThrow(() -> invalid(attempt));
        if (!lockUserRepository.lock(token.userId()) || !lockInvitationRepository.lock(token.invitationId())) {
            throw invalid(attempt);
        }
        Optional<OneTimeTokenPlatformAdminInvitation> stillValid = getOneTimeTokenService
                .findPlatformAdminInvitation(rawToken, PURPOSE);
        if (stillValid.isEmpty() || !stillValid.get().equals(token)) {
            throw invalid(attempt);
        }
        User user = getUserRepository.findById(token.userId()).orElseThrow(() -> invalid(attempt));
        PlatformAdminInvitation invitation = getInvitationRepository.findById(token.invitationId())
                .filter(PlatformAdminInvitation::isPending)
                .orElseThrow(() -> invalid(attempt));
        return new Usable(token, user, invitation);
    }

    private Optional<Outcome> checkCode(Usable usable, String confirmationCode, InvitationAttempt attempt) {
        PlatformAdminInvitation invitation = usable.invitation();
        if (ConfirmationCodeHash.matches(invitation.id(), confirmationCode, invitation.confirmationCodeHash())) {
            return Optional.empty();
        }
        int failed = updateInvitationRepository.recordFailedCodeAttempt(invitation.id());
        attempt.failedConfirmationCode();
        if (failed < MAX_FAILED_CODE_ATTEMPTS) {
            LOGGER.info("Platform-admin invitation code refused: userId={}, failedCodeAttempts={}",
                    invitation.userId().getId(), failed);
            return Optional.of(Outcome.WRONG_CODE);
        }
        revokeOneTimeTokenService.revokeForPlatformAdminInvitation(invitation.userId(), invitation.id(), PURPOSE);
        LOGGER.warn("Platform-admin invitation link revoked after {} wrong codes: userId={}", failed,
                invitation.userId().getId());
        return Optional.of(Outcome.ATTEMPTS_EXHAUSTED);
    }

    /**
     * Uses up the token, records the acceptance, grants the role, and, when the account has no email, sets the
     * invitation's destination email as its email: without one the person could not recover the password.
     */
    private void accept(String rawToken, Usable usable, InvitationAttempt attempt) {
        Optional<OneTimeTokenPlatformAdminInvitation> consumed = consumeOneTimeTokenService
                .consumeForPlatformAdminInvitation(rawToken, PURPOSE);
        if (consumed.isEmpty() || !consumed.get().equals(usable.token())) {
            throw invalid(attempt);
        }
        UserId userId = usable.token().userId();
        updateInvitationRepository.markAccepted(usable.invitation().id(), clock.instant());
        managePlatformAdminRepository.grant(userId);
        if (usable.user().getEmail() == null) {
            String email = EmailAddresses.blankToNull(usable.invitation().email());
            if (email != null) {
                updateUserEmailRepository.setEmail(userId, email);
            }
        }
        attempt.succeeded(usable.user());
    }

    private void log(Usable usable, String endpoint) {
        LOGGER.info("Platform-admin invitation accepted: userId={}, endpoint={}", usable.token().userId().getId(),
                endpoint);
    }

    private InvitationTokenInvalidException invalid(InvitationAttempt attempt) {
        attempt.failed();
        return new InvitationTokenInvalidException();
    }

    private record Usable(OneTimeTokenPlatformAdminInvitation token, User user, PlatformAdminInvitation invitation) {
    }
}
