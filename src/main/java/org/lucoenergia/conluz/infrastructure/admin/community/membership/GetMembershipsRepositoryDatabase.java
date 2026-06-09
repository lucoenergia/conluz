package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
@Repository
public class GetMembershipsRepositoryDatabase implements GetMembershipsRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public GetMembershipsRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository,
                                            CommunityJpaRepository communityJpaRepository,
                                            CommunityEntityMapper communityEntityMapper) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public List<CommunityMembership> findByCommunityId(UUID communityId) {
        CommunityEntity communityEntity = communityJpaRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));
        Community community = communityEntityMapper.map(communityEntity);

        return membershipJpaRepository.findByCommunityIdWithUser(communityId).stream()
                .map(e -> toDomain(e, community))
                .toList();
    }

    @Override
    public List<CommunityMembership> findByUserId(UUID userId) {
        return membershipJpaRepository.findByUserId(userId).stream()
                .map(e -> toDomain(e, e.getCommunity() != null ? communityEntityMapper.map(e.getCommunity()) : null))
                .toList();
    }

    private CommunityMembership toDomain(CommunityMembershipEntity entity, Community community) {
        return new CommunityMembership.Builder()
                .withId(entity.getId())
                .withUser(entity.getUser() != null ? entity.getUser().getUser() : null)
                .withCommunity(community)
                .withRole(entity.getRole())
                .withEnabled(entity.isEnabled())
                .build();
    }
}
