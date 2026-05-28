package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CreateMembershipServiceImpl implements CreateMembershipService {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final GetCommunityRepository getCommunityRepository;
    private final UserRepository userRepository;

    public CreateMembershipServiceImpl(CommunityMembershipJpaRepository membershipJpaRepository,
                                       GetCommunityRepository getCommunityRepository,
                                       UserRepository userRepository) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.getCommunityRepository = getCommunityRepository;
        this.userRepository = userRepository;
    }

    @Override
    public CommunityMembership create(UUID communityId, UUID userId, CommunityRole role) {
        Community community = getCommunityRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        org.lucoenergia.conluz.domain.shared.UserId.of(userId)));

        CommunityMembershipEntity entity = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(userEntity)
                .withCommunity(toEntity(community))
                .withRole(role)
                .withEnabled(true)
                .build();

        CommunityMembershipEntity saved = membershipJpaRepository.save(entity);
        return toDomain(saved, community, userEntity.getUser());
    }

    private CommunityEntity toEntity(Community community) {
        CommunityEntity entity = new CommunityEntity();
        entity.setId(community.getId());
        entity.setName(community.getName());
        entity.setCode(community.getCode());
        entity.setLegalId(community.getLegalId());
        entity.setAddress(community.getAddress());
        entity.setEnabled(community.isEnabled());
        return entity;
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
