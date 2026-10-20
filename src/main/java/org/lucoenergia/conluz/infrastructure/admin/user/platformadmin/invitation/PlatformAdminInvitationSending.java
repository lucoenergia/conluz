package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.admin.user.platformadmin.PendingPlatformAdmin;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.ConfirmationCode;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.GetPlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.UpdatePlatformAdminInvitationRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.invitation.PlatformAdminInvitationUnavailableException;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationLink;
import org.lucoenergia.conluz.infrastructure.shared.email.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Issues the link and the confirmation code of a platform-admin invitation the caller has locked, and emails the
 * link, never the code, once the transaction commits (ADR-0004 §3, §4).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PlatformAdminInvitationSending {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformAdminInvitationSending.class);

    private final CreateOneTimeTokenService createOneTimeTokenService;
    private final UpdatePlatformAdminInvitationRepository updateInvitationRepository;
    private final GetPlatformAdminInvitationRepository getInvitationRepository;
    private final EmailSender emailSender;
    private final PlatformAdminInvitationEmailFactory emailFactory;
    private final InvitationLink link;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public PlatformAdminInvitationSending(CreateOneTimeTokenService createOneTimeTokenService,
                                          UpdatePlatformAdminInvitationRepository updateInvitationRepository,
                                          GetPlatformAdminInvitationRepository getInvitationRepository,
                                          EmailSender emailSender,
                                          PlatformAdminInvitationEmailFactory emailFactory,
                                          InvitationLink link,
                                          EmailProperties emailProperties,
                                          Clock clock) {
        this.createOneTimeTokenService = createOneTimeTokenService;
        this.updateInvitationRepository = updateInvitationRepository;
        this.getInvitationRepository = getInvitationRepository;
        this.emailSender = emailSender;
        this.emailFactory = emailFactory;
        this.link = link;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    /**
     * Refuses when the server cannot send the invitation: the public web URL is not configured, or email sending is
     * disabled. Called before anything is looked up or written: an invitation recorded but never sent would show the
     * person as invited with nothing in their inbox. Logs one warning naming only the email category.
     *
     * @throws PlatformAdminInvitationUnavailableException if the server cannot send
     */
    public void requireAvailable() {
        if (!link.isConfigured()) {
            LOGGER.warn("Email {} not sent: the public web URL is not configured",
                    PlatformAdminInvitationEmailFactory.CATEGORY);
            throw new PlatformAdminInvitationUnavailableException();
        }
        if (!emailProperties.isEnabled()) {
            LOGGER.warn("Email {} not sent: sending is disabled", PlatformAdminInvitationEmailFactory.CATEGORY);
            throw new PlatformAdminInvitationUnavailableException();
        }
    }

    /**
     * Generates a new confirmation code, which replaces the invitation's previous one and resets its count of failed
     * attempts.
     *
     * @return the code, to be shown once to the inviter
     */
    public ConfirmationCode replaceCode(UUID invitationId) {
        ConfirmationCode code = ConfirmationCode.generate();
        updateInvitationRepository.replaceConfirmationCode(invitationId, ConfirmationCodeHash.of(invitationId, code));
        return code;
    }

    /**
     * Issues a new token for the invitation, which revokes its previous one, and emails its link to the invitation's
     * destination email once the transaction commits.
     */
    public void issueAndSend(UserId userId, UUID invitationId, String email) {
        RawOneTimeToken token = createOneTimeTokenService.issueForPlatformAdminInvitation(userId, invitationId,
                OneTimeTokenPurpose.PLATFORM_ADMIN_INVITATION);
        emailSender.send(emailFactory.build(email, link.forToken(token).orElseThrow()));
    }

    /**
     * @return the invitation's row as the list shows it, with when its link expires as the token store has it now
     */
    public PendingPlatformAdmin row(UserId userId) {
        return getInvitationRepository.findPendingRow(userId, clock.instant()).orElseThrow();
    }
}
