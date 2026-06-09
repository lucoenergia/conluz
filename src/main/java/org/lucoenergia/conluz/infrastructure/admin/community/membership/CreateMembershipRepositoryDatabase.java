package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
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
public class CreateMembershipRepositoryDatabase implements CreateMembershipRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final UserRepository userRepository;
    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;

    public CreateMembershipRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository,
                                              UserRepository userRepository,
                                              CommunityJpaRepository communityJpaRepository,
                                              CommunityEntityMapper communityEntityMapper) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.userRepository = userRepository;
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
    }

    @Override
    public CommunityMembership create(UUID communityId, UUID userId, CommunityRole role) {
        CommunityEntity communityEntity = communityJpaRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(UserId.of(userId)));

        CommunityMembershipEntity entity = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(userEntity)
                .withCommunity(communityEntity)
                .withRole(role)
                .withEnabled(true)
                .build();

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
