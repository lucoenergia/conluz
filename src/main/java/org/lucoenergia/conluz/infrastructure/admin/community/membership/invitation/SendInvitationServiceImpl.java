package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyInvitedException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.MembershipWithoutContactEmailException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.admin.user.token.RawOneTimeToken;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class SendInvitationServiceImpl implements SendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendInvitationServiceImpl.class);

    private final LockUserRepository lockUserRepository;
    private final LockMembershipRepository lockMembershipRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final CreateOneTimeTokenService createOneTimeTokenService;
    private final UpdateMembershipInvitationRepository updateMembershipInvitationRepository;
    private final EmailSender emailSender;
    private final InvitationEmailFactory emailFactory;
    private final InvitationLink link;
    private final Clock clock;

    public SendInvitationServiceImpl(LockUserRepository lockUserRepository,
                                     LockMembershipRepository lockMembershipRepository,
                                     GetMembershipsRepository getMembershipsRepository,
                                     CreateOneTimeTokenService createOneTimeTokenService,
                                     UpdateMembershipInvitationRepository updateMembershipInvitationRepository,
                                     EmailSender emailSender,
                                     InvitationEmailFactory emailFactory,
                                     InvitationLink link,
                                     Clock clock) {
        this.lockUserRepository = lockUserRepository;
        this.lockMembershipRepository = lockMembershipRepository;
        this.getMembershipsRepository = getMembershipsRepository;
        this.createOneTimeTokenService = createOneTimeTokenService;
        this.updateMembershipInvitationRepository = updateMembershipInvitationRepository;
        this.emailSender = emailSender;
        this.emailFactory = emailFactory;
        this.link = link;
        this.clock = clock;
    }

    @Override
    public CommunityMembership send(UUID communityId, UserId userId) {
        // Lock order: user row, then membership row, then token rows
        if (!lockUserRepository.lock(userId) || !lockMembershipRepository.lock(userId, communityId)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        // Read under the lock, so two invitations of the same membership run one after the other
        CommunityMembership membership = getMembershipsRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        if (!membership.isPending()) {
            throw new MembershipAlreadyExistsException(communityId, userId.getId());
        }
        if (membership.getInvitedAt() != null) {
            throw new MembershipAlreadyInvitedException(communityId, userId.getId());
        }
        if (membership.getContactEmail() == null) {
            throw new MembershipWithoutContactEmailException(communityId, userId.getId());
        }
        if (!link.isConfigured()) {
            // No token is issued and the invitation instant stays empty: nothing was sent
            LOGGER.warn("Email {} not sent: the public web URL is not configured", InvitationEmailFactory.CATEGORY);
            return membership;
        }

        RawOneTimeToken token = createOneTimeTokenService.issueForMembership(userId, communityId,
                OneTimeTokenPurpose.INVITATION);
        CommunityMembership invited = updateMembershipInvitationRepository.markInvited(userId, communityId,
                clock.instant());
        // Sent once the transaction commits; the recipient is the member record's contact email, never the
        // account's email
        emailSender.send(emailFactory.build(invited.getContactEmail(), invited.getCommunity().getName(),
                link.forToken(token).orElseThrow()));

        LOGGER.info("Member invited: userId={}, communityId={}", userId.getId(), communityId);
        return invited;
    }
}
