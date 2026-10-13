package org.lucoenergia.conluz.domain.admin.user.token;

import org.junit.jupiter.api.AfterEach;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.DeleteMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.lock.LockMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.lock.LockUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The tests of the membership-scoped token operations. Like {@link BaseOneTimeTokenServiceTest}, they run against
 * the real database and commit. Users, communities and pending memberships are created the way production creates
 * them, and are deleted afterwards, which deletes their tokens too.
 */
abstract class BaseMembershipOneTimeTokenServiceTest extends BaseOneTimeTokenServiceTest {

    protected static final OneTimeTokenPurpose INVITATION = OneTimeTokenPurpose.INVITATION;
    protected static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

    @Autowired
    protected CreateUserRepository createUserRepository;
    @Autowired
    protected CreateCommunityRepository createCommunityRepository;
    @Autowired
    protected CreateMembershipRepository createMembershipRepository;
    @Autowired
    protected DeleteMembershipRepository deleteMembershipRepository;
    @Autowired
    protected CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    protected LockUserRepository lockUserRepository;
    @Autowired
    protected LockMembershipRepository lockMembershipRepository;
    @Autowired
    protected RevokeOneTimeTokenService revokeService;

    private final List<UUID> createdUsers = new ArrayList<>();
    private final List<UUID> createdCommunities = new ArrayList<>();

    /**
     * Runs before the cleanup of {@link BaseOneTimeTokenServiceTest}: memberships first, since they reference both
     * their user and their community.
     */
    @AfterEach
    void deleteTheMembershipsUsersAndCommunities() {
        createdCommunities.forEach(id -> jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", id));
        createdUsers.forEach(id -> jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ?", id));
        createdUsers.forEach(id -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", id));
        createdCommunities.forEach(id -> jdbcTemplate.update("DELETE FROM communities WHERE id = ?", id));
    }

    protected UserId newPerson() {
        User user = createUserRepository.create(UserMother.randomUser());
        createdUsers.add(user.getId());
        return UserId.of(user.getId());
    }

    protected UUID newCommunity() {
        UUID id = createCommunityRepository.create(CommunityMother.random().build()).getId();
        createdCommunities.add(id);
        return id;
    }

    /**
     * Creates a pending membership of the user in a new community, and returns that community.
     */
    protected UUID newPendingMembership(UserId user) {
        UUID community = newCommunity();
        createMembershipRepository.create(community, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, user.getId(), community);
        return community;
    }

    protected UUID membershipId(UserId user, UUID community) {
        return communityMembershipJpaRepository.findByUserIdAndCommunityId(user.getId(), community).orElseThrow()
                .getId();
    }

    protected RawOneTimeToken invite(UserId user, UUID community) {
        RawOneTimeToken token = createService.issueForMembership(user, community, INVITATION);
        remember(token.value());
        return token;
    }

    protected Optional<OneTimeTokenMembership> consumeInvitation(String rawToken) {
        return consumeService.consumeForMembership(rawToken, INVITATION);
    }

    protected OneTimeTokenMembership expected(UserId user, UUID community) {
        return new OneTimeTokenMembership(membershipId(user, community), user, community);
    }

    protected int activeTokenCount(UserId user, UUID community) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token "
                        + "WHERE membership_id = ? AND used_at IS NULL AND revoked_at IS NULL", Integer.class,
                membershipId(user, community));
    }

    protected int tokenCount(UserId user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE user_id = ?", Integer.class,
                user.getId());
    }
}
