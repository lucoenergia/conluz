package org.lucoenergia.conluz.infrastructure.admin.user.auth;

import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.uuid.UUIDValidator;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserDetailsServiceFromDatabase implements UserDetailsService {

    private final UserRepository userRepository;
    private final CommunityMembershipJpaRepository communityMembershipJpaRepository;

    public UserDetailsServiceFromDatabase(UserRepository userRepository,
                                          CommunityMembershipJpaRepository communityMembershipJpaRepository) {
        this.userRepository = userRepository;
        this.communityMembershipJpaRepository = communityMembershipJpaRepository;
    }

    @Override
    public UserDetails loadUserByUsername(final String userId) throws UsernameNotFoundException {
        Optional<UserEntity> userEntity = Optional.empty();
        if (UUIDValidator.validate(userId)) {
            userEntity = userRepository.findById(UUID.fromString(userId));
        }
        if (userEntity.isEmpty()) {
            userEntity = userRepository.findByPersonalId(userId);
        }
        if (userEntity.isEmpty()) {
            throw new UsernameNotFoundException(userId);
        }
        User user = userEntity.get().getUser();

        // Load community memberships for the user
        List<CommunityMembershipEntity> membershipEntities =
                communityMembershipJpaRepository.findByUser_Id(user.getId());
        List<CommunityMembership> memberships = membershipEntities.stream()
                .map(this::mapMembership)
                .toList();
        user.setMemberships(memberships);

        return user;
    }

    private CommunityMembership mapMembership(CommunityMembershipEntity entity) {
        Community community = new Community.Builder()
                .withId(entity.getCommunity().getId())
                .withName(entity.getCommunity().getName())
                .withCode(entity.getCommunity().getCode())
                .withEnabled(entity.getCommunity().isEnabled())
                .build();
        // Only id is needed for the membership user reference to avoid circular loading
        User membershipUser = new User();
        membershipUser.setId(entity.getUser().getId());
        return new CommunityMembership.Builder()
                .withId(entity.getId())
                .withUser(membershipUser)
                .withCommunity(community)
                .withRole(entity.getRole())
                .withEnabled(entity.isEnabled())
                .build();
    }
}
