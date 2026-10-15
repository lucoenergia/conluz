package org.lucoenergia.conluz.infrastructure.admin.community.admins;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.admins.CommunityAdmin;
import org.lucoenergia.conluz.domain.admin.community.admins.RevokeCommunityAdminInvitationService;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationRevoking;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation.InvitationSending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RevokeCommunityAdminInvitationServiceImpl implements RevokeCommunityAdminInvitationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokeCommunityAdminInvitationServiceImpl.class);

    private final GetCommunityRepository getCommunityRepository;
    private final InvitationRevoking revoking;
    private final InvitationSending sending;

    public RevokeCommunityAdminInvitationServiceImpl(GetCommunityRepository getCommunityRepository,
                                                     InvitationRevoking revoking, InvitationSending sending) {
        this.getCommunityRepository = getCommunityRepository;
        this.revoking = revoking;
        this.sending = sending;
    }

    @Override
    public CommunityAdmin revoke(UUID communityId, UserId userId) {
        getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        // A member's membership is answered as a missing one: these endpoints act on admin memberships only
        InvitationRevoking.RevokedInvitation revoked = revoking.revoke(communityId, userId,
                membership -> membership.getRole() == CommunityRole.COMMUNITY_ADMIN);

        LOGGER.info("Community admin invitation revoked: userId={}, communityId={}, revoked={}", userId.getId(),
                communityId, revoked.revoked());
        CommunityMembership membership = revoked.membership();
        return new CommunityAdmin(membership.getUser().getPersonalId(), membership,
                sending.withInvitation(membership).invitationExpiresAt());
    }
}
