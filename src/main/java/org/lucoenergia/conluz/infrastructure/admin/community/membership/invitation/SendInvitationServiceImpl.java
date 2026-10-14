package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.SendInvitationService;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
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
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SendInvitationServiceImpl implements SendInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendInvitationServiceImpl.class);

    private final LockMembershipRepository lockMembershipRepository;
    private final GetMembershipsRepository getMembershipsRepository;
    private final CreateOneTimeTokenService createOneTimeTokenService;
    private final UpdateMembershipInvitationRepository updateMembershipInvitationRepository;
    private final EmailSender emailSender;
    private final InvitationEmailFactory emailFactory;
    private final InvitationLink link;
    private final Clock clock;

    public SendInvitationServiceImpl(LockMembershipRepository lockMembershipRepository,
                                     GetMembershipsRepository getMembershipsRepository,
                                     CreateOneTimeTokenService createOneTimeTokenService,
                                     UpdateMembershipInvitationRepository updateMembershipInvitationRepository,
                                     EmailSender emailSender,
                                     InvitationEmailFactory emailFactory,
                                     InvitationLink link,
                                     Clock clock) {
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
    public Optional<CommunityMembership> send(CommunityMembership membership) {
        if (!link.isConfigured()) {
            // No token is issued and the invitation instant stays empty: nothing was sent
            LOGGER.warn("Email {} not sent: the public web URL is not configured", InvitationEmailFactory.CATEGORY);
            return Optional.empty();
        }
        UserId userId = UserId.of(membership.getUser().getId());
        UUID communityId = membership.getCommunity().getId();

        if (!lockMembershipRepository.lock(userId, communityId)) {
            throw new MembershipNotFoundException(communityId, userId.getId());
        }
        // Read again under the lock: the state the caller holds may be stale
        CommunityMembership locked = getMembershipsRepository.findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));
        if (!locked.isPending()) {
            throw new MembershipAlreadyExistsException(communityId, userId.getId());
        }

        RawOneTimeToken token = createOneTimeTokenService.issueForMembership(userId, communityId,
                OneTimeTokenPurpose.INVITATION);
        CommunityMembership invited = updateMembershipInvitationRepository.markInvited(userId, communityId,
                clock.instant());
        // Sent once the transaction commits; the recipient is the member record's contact email, never the
        // account's email
        emailSender.send(emailFactory.build(invited.getContactEmail(), invited.getCommunity().getName(),
                link.forToken(token).orElseThrow()));
        return Optional.of(invited);
    }
}
