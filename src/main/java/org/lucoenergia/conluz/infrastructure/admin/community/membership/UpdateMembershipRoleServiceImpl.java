package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UpdateMembershipRoleServiceImpl implements UpdateMembershipRoleService {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final GetCommunityRepository getCommunityRepository;
    private final UserRepository userRepository;

    public UpdateMembershipRoleServiceImpl(CommunityMembershipJpaRepository membershipJpaRepository,
                                           GetCommunityRepository getCommunityRepository,
                                           UserRepository userRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.getCommunityRepository = getCommunityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public CommunityMembership updateRole(UUID communityId, UUID userId, CommunityRole role) {
        Community community = getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(UserId.of(userId)));

        CommunityMembershipEntity entity = membershipJpaRepository.findByUserId(userId).stream()
                .filter(m -> m.getCommunity() != null && communityId.equals(m.getCommunity().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found"));

        entity.setRole(role);
        CommunityMembershipEntity saved = membershipJpaRepository.save(entity);
        return toDomain(saved, community, userEntity.getUser());
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
