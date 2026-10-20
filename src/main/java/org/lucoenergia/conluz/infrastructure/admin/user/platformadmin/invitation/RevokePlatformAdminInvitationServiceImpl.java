package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.LockPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitation;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminNotInvitedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.RevokePlatformAdminInvitationService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RevokeOneTimeTokenService;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RevokePlatformAdminInvitationServiceImpl implements RevokePlatformAdminInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokePlatformAdminInvitationServiceImpl.class);

    private final LockUserRepository lockUserRepository;
    private final LockPlatformAdminInvitationRepository lockInvitationRepository;
    private final GetPlatformAdminInvitationRepository getInvitationRepository;
    private final RevokeOneTimeTokenService revokeOneTimeTokenService;
    private final PlatformAdminInvitationSending sending;

    public RevokePlatformAdminInvitationServiceImpl(LockUserRepository lockUserRepository,
                                                    LockPlatformAdminInvitationRepository lockInvitationRepository,
                                                    GetPlatformAdminInvitationRepository getInvitationRepository,
                                                    RevokeOneTimeTokenService revokeOneTimeTokenService,
                                                    PlatformAdminInvitationSending sending) {
        this.lockUserRepository = lockUserRepository;
        this.lockInvitationRepository = lockInvitationRepository;
        this.getInvitationRepository = getInvitationRepository;
        this.revokeOneTimeTokenService = revokeOneTimeTokenService;
        this.sending = sending;
    }

    @Override
    public PendingPlatformAdmin revoke(UserId userId) {
        // Lock order: user row, then invitation row, then token rows
        if (!lockUserRepository.lock(userId)) {
            throw new UserNotFoundException(userId);
        }
        if (!lockInvitationRepository.lockPending(userId)) {
            throw new PlatformAdminNotInvitedException(userId);
        }
        PlatformAdminInvitation invitation = getInvitationRepository.findPendingByUserId(userId).orElseThrow();
        boolean revoked = revokeOneTimeTokenService.revokeForPlatformAdminInvitation(userId, invitation.id(),
                OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION);
        LOGGER.info("Platform-admin invitation revoked: userId={}, revoked={}", userId.getId(), revoked);
        return sending.row(userId);
    }
}
