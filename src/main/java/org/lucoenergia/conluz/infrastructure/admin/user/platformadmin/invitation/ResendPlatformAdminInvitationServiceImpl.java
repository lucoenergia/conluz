package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCode;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.InvitedPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.LockPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitationResendLimitException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminNotInvitedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ResendPlatformAdminInvitationService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationResending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@Transactional
public class ResendPlatformAdminInvitationServiceImpl implements ResendPlatformAdminInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendPlatformAdminInvitationServiceImpl.class);

    private final LockUserRepository lockUserRepository;
    private final LockPlatformAdminInvitationRepository lockInvitationRepository;
    private final GetPlatformAdminInvitationRepository getInvitationRepository;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final PlatformAdminInvitationSending sending;
    private final Clock clock;

    public ResendPlatformAdminInvitationServiceImpl(LockUserRepository lockUserRepository,
                                                    LockPlatformAdminInvitationRepository lockInvitationRepository,
                                                    GetPlatformAdminInvitationRepository getInvitationRepository,
                                                    GetOneTimeTokenService getOneTimeTokenService,
                                                    PlatformAdminInvitationSending sending,
                                                    Clock clock) {
        this.lockUserRepository = lockUserRepository;
        this.lockInvitationRepository = lockInvitationRepository;
        this.getInvitationRepository = getInvitationRepository;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.sending = sending;
        this.clock = clock;
    }

    @Override
    public InvitedPlatformAdmin resend(UserId userId) {
        sending.requireAvailable();

        // Lock order: user row, then invitation row, then token rows
        if (!lockUserRepository.lock(userId)) {
            throw new UserNotFoundException(userId);
        }
        if (!lockInvitationRepository.lockPending(userId)) {
            throw new PlatformAdminNotInvitedException(userId);
        }
        PlatformAdminInvitation invitation = getInvitationRepository.findPendingByUserId(userId).orElseThrow();

        // Counted under the invitation's lock, so two concurrent resends at the limit cannot both pass
        Instant now = clock.instant();
        Instant since = now.minus(InvitationResending.WINDOW);
        long sent = getOneTimeTokenService.countIssuedForPlatformAdminInvitationSince(invitation.id(),
                OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION, since);
        if (sent >= InvitationResending.MAX_SENDS_PER_WINDOW) {
            Instant oldest = getOneTimeTokenService.findEarliestIssuedForPlatformAdminInvitationSince(invitation.id(),
                    OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION, since).orElseThrow();
            // A send exactly WINDOW old still counts, so a whole number of seconds is followed by one more
            long retryAfterSeconds = Duration.between(now, oldest.plus(InvitationResending.WINDOW)).getSeconds() + 1;
            LOGGER.info("Platform-admin invitation resend refused: userId={}, retryAfterSeconds={}", userId.getId(),
                    retryAfterSeconds);
            throw new PlatformAdminInvitationResendLimitException(userId, retryAfterSeconds);
        }

        ConfirmationCode code = sending.replaceCode(invitation.id());
        sending.issueAndSend(userId, invitation.id(), invitation.email());
        LOGGER.info("Platform-admin invitation resent: userId={}", userId.getId());

        return new InvitedPlatformAdmin(sending.row(userId), code);
    }
}
