package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.ResendCommunityAdminInvitationService;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationResending;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ResendCommunityAdminInvitationServiceImpl implements ResendCommunityAdminInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendCommunityAdminInvitationServiceImpl.class);

    private final GetCommunityRepository getCommunityRepository;
    private final InvitationResending resending;
    private final InvitationSending sending;

    public ResendCommunityAdminInvitationServiceImpl(GetCommunityRepository getCommunityRepository,
                                                     InvitationResending resending, InvitationSending sending) {
        this.getCommunityRepository = getCommunityRepository;
        this.resending = resending;
        this.sending = sending;
    }

    @Override
    public CommunityAdmin resend(UUID communityId, UserId userId) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        // A member's membership is answered as a missing one: these endpoints act on admin memberships only
        CommunityMembership invited = resending.resend(communityId, userId,
                membership -> membership.getRole() == CommunityRole.COMMUNITY_ADMIN);

        LOGGER.info("Community admin invitation resent: userId={}, communityId={}", userId.getId(), communityId);
        return new CommunityAdmin(invited.getUser().getPersonalId(), invited,
                sending.withInvitation(invited).invitationExpiresAt());
    }
}
