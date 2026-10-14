package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.MembershipNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.invitation.UpdateMembershipInvitationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Transactional
@Repository
public class UpdateMembershipInvitationRepositoryDatabase implements UpdateMembershipInvitationRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public UpdateMembershipInvitationRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository,
                                                        CommunityEntityMapper communityEntityMapper) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public CommunityMembership markInvited(UserId userId, UUID communityId, Instant invitedAt) {
        CommunityMembershipEntity entity = membershipJpaRepository
                .findByUserIdAndCommunityId(userId.getId(), communityId)
                .orElseThrow(() -> new MembershipNotFoundException(communityId, userId.getId()));

        entity.setInvitedAt(invitedAt);
        CommunityMembershipEntity saved = membershipJpaRepository.save(entity);

        return new CommunityMembership.Builder()
                .withId(saved.getId())
                .withUser(saved.getUser().getUser())
                .withCommunity(communityEntityMapper.map(saved.getCommunity()))
                .withRole(saved.getRole())
                .withEnabled(saved.isEnabled())
                .withInvestmentEur(saved.getInvestmentEur())
                .withFullName(saved.getFullName())
                .withMemberNumber(saved.getMemberNumber())
                .withContactEmail(saved.getContactEmail())
                .withAddress(saved.getAddress())
                .withPhoneNumber(saved.getPhoneNumber())
                .withAcceptedAt(saved.getAcceptedAt())
                .withInvitedBy(saved.getInvitedBy())
                .withInvitedAt(saved.getInvitedAt())
                .build();
    }
}
