package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipInvitationUnavailableException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MembershipWithInvitation;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.GetOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.shared.email.EmailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The steps every invitation flow shares: sending the first invitation, resending it, revoking it and sending first
 * invitations in bulk. Each flow decides, from the membership read under its lock, whether to send.
 *
 * <p>Runs only inside the caller's transaction: the locks it takes are held until that transaction ends, and the email
 * is handed over once it commits.</p>
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class InvitationSending {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvitationSending.class);

    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final CreateOneTimeTokenService createOneTimeTokenService;
    private final GetOneTimeTokenService getOneTimeTokenService;
    private final UpdateMembershipInvitationRepository updateMembershipInvitationRepository;
    private final EmailSender emailSender;
    private final InvitationEmailFactory emailFactory;
    private final InvitationLink link;
    private final EmailProperties emailProperties;
    private final Clock clock;

    public InvitationSending(LockUserRepository lockUserRepository,
                             LockMembershipRepository lockMembershipRepository,
                             GetMembershipsRepository getMembershipsRepository,
                             CreateOneTimeTokenService createOneTimeTokenService,
                             GetOneTimeTokenService getOneTimeTokenService,
                             UpdateMembershipInvitationRepository updateMembershipInvitationRepository,
                             EmailSender emailSender,
                             InvitationEmailFactory emailFactory,
                             InvitationLink link,
                             EmailProperties emailProperties,
                             Clock clock) {
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.createOneTimeTokenService = createOneTimeTokenService;
        this.getOneTimeTokenService = getOneTimeTokenService;
        this.updateMembershipInvitationRepository = updateMembershipInvitationRepository;
        this.emailSender = emailSender;
        this.emailFactory = emailFactory;
        this.link = link;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    /**
     * Refuses when the server cannot send an invitation: the public web URL is not configured, or email sending is
     * disabled. Called before anything is locked or issued: an invitation recorded but never sent would tell the
     * community admin the member was invited with nothing in the member's inbox. Logs one warning naming only the
     * email category.
     *
     * @throws MembershipInvitationUnavailableException if the server cannot send
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void requireAvailable() {
        if (!link.isConfigured()) {
            LOGGER.warn("Email {} not sent: the public web URL is not configured", InvitationEmailFactory.CATEGORY);
            throw new MembershipInvitationUnavailableException();
        }
        if (!emailProperties.isEnabled()) {
            LOGGER.warn("Email {} not sent: sending is disabled", InvitationEmailFactory.CATEGORY);
            throw new MembershipInvitationUnavailableException();
        }
    }

    /**
     * Locks the user row, then the membership row, then reads the membership: what the caller decides from it holds
     * until its transaction ends, so two invitation flows on the same membership run one after the other.
     *
     * @throws MembershipNotFoundException if the user has no membership in the community
     */
    public CommunityMembership lockAndRead(UUID communityId, UserId userId) {
        // Lock order: user row, then membership row, then token rows
        if (!lockUserRepository.lock(userId) || !lockMembershipRepository.lock(userId, communityId)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        return getMembershipsRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
    }

    /**
     * Issues a new {@code INVITATION} token for a membership locked by {@link #lockAndRead}, which revokes its
     * previous one, records now as its invitation instant, and emails the link to its contact email once the
     * transaction commits.
     *
     * @return the membership as invited
     */
    public CommunityMembership issueAndSend(CommunityMembership membership) {
        UserId userId = UserId.of(membership.getUser().getId());
        UUID communityId = membership.getCommunity().getId();
        RawOneTimeToken token = createOneTimeTokenService.issueForMembership(userId, communityId,
                OneTimeTokenPurpose.INVITATION);
        CommunityMembership invited = updateMembershipInvitationRepository.markInvited(userId, communityId,
                clock.instant());
        // Sent once the transaction commits; the recipient is the member record's contact email as it is now, never
        // the account's email
        emailSender.send(emailFactory.build(invited.getContactEmail(), invited.getCommunity().getName(),
                link.forToken(token).orElseThrow()));
        return invited;
    }

    /**
     * @return the membership, with when its invitation link expires as the token store has it now
     */
    public MembershipWithInvitation withInvitation(CommunityMembership membership) {
        return new MembershipWithInvitation(membership, getOneTimeTokenService
                .findValidExpiryForMembership(UserId.of(membership.getUser().getId()),
                        membership.getCommunity().getId(), OneTimeTokenPurpose.INVITATION)
                .orElse(null));
    }
}
