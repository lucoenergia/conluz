package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCode;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.CreatePlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.InvitePlatformAdminService;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.InvitedPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.LockPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.UserAlreadyPlatformAdminException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.lucoenergia.conluz.infrastructure.admin.user.create.PasswordlessAccountRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class InvitePlatformAdminServiceImpl implements InvitePlatformAdminService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitePlatformAdminServiceImpl.class);

    private final InvitationSending invitationSending;
    private final PasswordlessAccountRegistration passwordlessAccountRegistration;
    private final LockUserRepository lockUserRepository;
    private final GetUserRepository getUserRepository;
    private final LockPlatformAdminInvitationRepository lockInvitationRepository;
    private final CreatePlatformAdminInvitationRepository createInvitationRepository;
    private final PlatformAdminInvitationSending sending;
    private final Clock clock;

    public InvitePlatformAdminServiceImpl(InvitationSending invitationSending,
                                          PasswordlessAccountRegistration passwordlessAccountRegistration,
                                          LockUserRepository lockUserRepository,
                                          GetUserRepository getUserRepository,
                                          LockPlatformAdminInvitationRepository lockInvitationRepository,
                                          CreatePlatformAdminInvitationRepository createInvitationRepository,
                                          PlatformAdminInvitationSending sending,
                                          Clock clock) {
        this.invitationSending = invitationSending;
        this.passwordlessAccountRegistration = passwordlessAccountRegistration;
        this.lockUserRepository = lockUserRepository;
        this.getUserRepository = getUserRepository;
        this.lockInvitationRepository = lockInvitationRepository;
        this.createInvitationRepository = createInvitationRepository;
        this.sending = sending;
        this.clock = clock;
    }

    @Override
    public InvitedPlatformAdmin invite(UserId invitedBy, UserPersonalId personalId, String fullName, String email) {
        // Before anything is looked up or written: an invitation recorded but never sent would show the person as
        // invited with nothing in their inbox
        invitationSending.requireAvailable();

        String normalized = passwordlessAccountRegistration.requireValid(personalId);
        // An existing account is found silently and never written: everything below reads it only to refuse, and
        // the invitation holds the name and email the inviter typed
        UserId userId = passwordlessAccountRegistration.findOrCreate(normalized, fullName, email);

        // Lock order: user row, then invitation row, then token rows. Holding the user row makes two invitations of
        // the same person run one after the other, so the second one finds the first one's invitation
        lockUserRepository.lock(userId);
        User user = getUserRepository.findById(userId).orElseThrow();
        if (user.isPlatformAdmin()) {
            throw new UserAlreadyPlatformAdminException(userId);
        }
        if (lockInvitationRepository.lockPending(userId)) {
            throw new PlatformAdminAlreadyInvitedException(userId);
        }

        UUID invitationId = UUID.randomUUID();
        ConfirmationCode code = ConfirmationCode.generate();
        createInvitationRepository.create(invitationId, userId, email, fullName, invitedBy, clock.instant(),
                ConfirmationCodeHash.of(invitationId, code));
        sending.issueAndSend(userId, invitationId, email);

        LOGGER.info("Platform admin invited: personalId={}, invitedBy={}", UserPersonalId.mask(normalized),
                invitedBy.getId());

        return new InvitedPlatformAdmin(sending.row(userId), code);
    }
}
