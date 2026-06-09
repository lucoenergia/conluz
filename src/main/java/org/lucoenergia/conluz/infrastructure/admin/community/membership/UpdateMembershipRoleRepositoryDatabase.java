package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional
@Repository
public class UpdateMembershipRoleRepositoryDatabase implements UpdateMembershipRoleRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final UserRepository userRepository;
    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public UpdateMembershipRoleRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository,
                                                  UserRepository userRepository,
                                                  CommunityJpaRepository communityJpaRepository,
                                                  CommunityEntityMapper communityEntityMapper) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.userRepository = userRepository;
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public CommunityMembership updateRole(UUID communityId, UUID userId, CommunityRole role) {
        CommunityEntity communityEntity = communityJpaRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(UserId.of(userId)));

        CommunityMembershipEntity entity = membershipJpaRepository.findByUserId(userId).stream()
                .filter(m -> m.getCommunity() != null && communityId.equals(m.getCommunity().getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Membership not found"));

        entity.setRole(role);
        CommunityMembershipEntity saved = membershipJpaRepository.save(entity);

        return new CommunityMembership.Builder()
                .withId(saved.getId())
                .withUser(userEntity.getUser())
                .withCommunity(communityEntityMapper.map(communityEntity))
                .withRole(saved.getRole())
                .withEnabled(saved.isEnabled())
                .build();
    }
}
