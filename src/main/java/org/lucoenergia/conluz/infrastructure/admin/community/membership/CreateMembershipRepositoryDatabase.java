package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.email.EmailAddresses;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Transactional
@Repository
public class CreateMembershipRepositoryDatabase implements CreateMembershipRepository {

    private final CommunityMembershipJpaRepository membershipJpaRepository;
    private final UserRepository userRepository;
    private final CommunityJpaRepository communityJpaRepository;
    private final CommunityEntityMapper communityEntityMapper;
    private final Clock clock;

    public CreateMembershipRepositoryDatabase(CommunityMembershipJpaRepository membershipJpaRepository,
                                              UserRepository userRepository,
                                              CommunityJpaRepository communityJpaRepository,
                                              CommunityEntityMapper communityEntityMapper,
                                              Clock clock) {
        this.membershipJpaRepository = membershipJpaRepository;
        this.userRepository = userRepository;
        this.communityJpaRepository = communityJpaRepository;
        this.communityEntityMapper = communityEntityMapper;
        this.clock = clock;
    }

    @Override
    public CommunityMembership create(UUID communityId, UUID userId, CommunityRole role) {
        CommunityEntity communityEntity = communityJpaRepository.findById(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId));

        UserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(UserId.of(userId)));

        // Checked here rather than left to the (user_id, community_id) unique constraint, whose
        // DataIntegrityViolationException nothing maps and which therefore surfaced as a 500.
        // A narrow race remains: two concurrent creates can both pass this and one will still hit
        // the constraint. The window is small and the outcome is the same either way -- one
        // membership exists -- so it is not worth serialising the endpoint over.
        if (membershipJpaRepository.existsByUserIdAndCommunityId(userId, communityId)) {
            throw new MembershipAlreadyExistsException(communityId, userId);
        }

        // The member record starts as a copy of the user's data, and the membership is accepted on
        // creation: every path that reaches this creates an active membership.
        CommunityMembershipEntity entity = new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(userEntity)
                .withCommunity(communityEntity)
                .withRole(role)
                .withEnabled(true)
                .withFullName(userEntity.getFullName())
                .withMemberNumber(userEntity.getNumber())
                .withContactEmail(EmailAddresses.blankToNull(userEntity.getEmail()))
                .withAddress(userEntity.getAddress())
                .withPhoneNumber(userEntity.getPhoneNumber())
                .withAcceptedAt(clock.instant())
                .build();

        CommunityMembershipEntity saved = membershipJpaRepository.save(entity);

        return new CommunityMembership.Builder()
                .withId(saved.getId())
                .withUser(userEntity.getUser())
                .withCommunity(communityEntityMapper.map(communityEntity))
                .withRole(saved.getRole())
                .withEnabled(saved.isEnabled())
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
