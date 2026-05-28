package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetMembershipsServiceImpl implements GetMembershipsService {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final GetCommunityRepository getCommunityRepository;

    public GetMembershipsServiceImpl(CommunityMembershipJpaRepository membershipJpaRepository,
                                     GetCommunityRepository getCommunityRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.getCommunityRepository = getCommunityRepository;
    }

    @Override
    public List<CommunityMembership> findByCommunityId(UUID communityId) {
        Community community = getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        List<CommunityMembershipEntity> entities = membershipJpaRepository.findByCommunityId(communityId);
        return entities.stream()
                .map(e -> toDomain(e, community, e.getUser() != null ? e.getUser().getUser() : null))
                .toList();
    }

    private CommunityMembership toDomain(CommunityMembershipEntity entity, Community community, User user) {
        return new CommunityMembership.Builder()
                .withId(entity.getId())
                .withUser(user)
                .withCommunity(community)
                .withRole(entity.getRole())
                .withEnabled(entity.isEnabled())
                .build();
    }
}
