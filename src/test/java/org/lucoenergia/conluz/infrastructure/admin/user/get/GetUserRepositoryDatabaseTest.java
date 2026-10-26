package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Transactional
class GetUserRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;


    @Test
    void testFindByPersonalId() {

        UserEntity userOne = UserMother.randomUserEntity();
        // Create some users
        userRepository.saveAll(Arrays.asList(
                userOne,
                UserMother.randomUserEntity(),
                UserMother.randomUserEntity()
        ));
        UserPersonalId userPersonalId = UserPersonalId.of(userOne.getPersonalId());

        Optional<User> result = getUserRepository.findByPersonalId(userPersonalId);

        Assertions.assertTrue(result.isPresent());
    }

    @Test
    void getDefaultAdminUserIsEmptyWhenNoUserHasNumberZero() {
        renumberUsersWithNumberZero();

        Assertions.assertTrue(getUserRepository.getDefaultAdminUser().isEmpty());
    }

    @Test
    void getDefaultAdminUserReturnsTheUserWithNumberZero() {
        renumberUsersWithNumberZero();
        UserEntity admin = UserMother.randomUserEntity();
        admin.setNumber(0);
        userRepository.saveAndFlush(admin);

        Optional<User> result = getUserRepository.getDefaultAdminUser();

        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(admin.getId(), result.get().getId());
    }

    /**
     * Other test classes may have committed a default admin to the shared database. Renumbering
     * them inside this test's transaction (rolled back afterwards) makes "no user has number 0"
     * true here without deleting rows that other tables reference.
     */
    private void renumberUsersWithNumberZero() {
        Optional<UserEntity> existing;
        while ((existing = userRepository.findFirstByNumber(0)).isPresent()) {
            existing.get().setNumber(-1);
            userRepository.saveAndFlush(existing.get());
        }
    }

    @Test
    void findAllVisibleReturnsTheCallerAndTheEnabledMembersOfTheGivenCommunities() {
        // Given
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        Community communityC = createCommunityRepository.create(CommunityMother.random().build());
        UserEntity inA = persistUser(1_000_010);
        UserEntity inB = persistUser(1_000_020);
        UserEntity inAAndC = persistUser(1_000_030);
        UserEntity onlyInC = persistUser(1_000_040);
        UserEntity disabledInA = persistUser(1_000_050);
        UserEntity self = persistUser(1_000_015);
        persistMembership(inA, communityA, true);
        persistMembership(inB, communityB, true);
        persistMembership(inAAndC, communityA, true);
        persistMembership(inAAndC, communityC, true);
        persistMembership(onlyInC, communityC, true);
        persistMembership(disabledInA, communityA, false);

        // When
        PagedResult<User> result = getUserRepository.findAllVisible(byNumber(0, 10), self.getId(),
                Set.of(communityA.getId(), communityB.getId()));

        // Then: each user once, the caller (in no community) sorted among the others, not appended
        Assertions.assertEquals(List.of(inA.getId(), self.getId(), inB.getId(), inAAndC.getId()), ids(result));
        Assertions.assertEquals(4, result.getTotalElements());
    }

    @Test
    @DisplayName("MEM-004 findAllVisible leaves out the users whose membership in the given communities is pending")
    void findAllVisibleLeavesOutPendingMembers() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User self = createUserRepository.create(UserMother.randomUser());
        User active = createUserRepository.create(UserMother.randomUser());
        User pending = createUserRepository.create(UserMother.randomUser());
        createMembershipRepository.create(community.getId(), active.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipRepository.create(community.getId(), pending.getId(), CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, pending.getId(), community.getId());

        // When
        PagedResult<User> result = getUserRepository.findAllVisible(byNumber(0, 10), self.getId(),
                Set.of(community.getId()));

        // Then
        Assertions.assertEquals(Set.of(self.getId(), active.getId()), Set.copyOf(ids(result)));
        Assertions.assertEquals(2, result.getTotalElements());
    }

    @Test
    void findAllVisiblePaginatesOverTheWholeScope() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        UserEntity first = persistUser(1_000_110);
        UserEntity self = persistUser(1_000_120);
        UserEntity third = persistUser(1_000_130);
        persistMembership(first, community, true);
        persistMembership(third, community, true);

        // When
        PagedResult<User> secondPage = getUserRepository.findAllVisible(byNumber(1, 2), self.getId(),
                Set.of(community.getId()));

        // Then
        Assertions.assertEquals(List.of(third.getId()), ids(secondPage));
        Assertions.assertEquals(3, secondPage.getTotalElements());
    }

    @Test
    void findAllVisibleReturnsOnlyTheCallerForAnEmptySetOfCommunities() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        persistMembership(persistUser(1_000_210), community, true);
        UserEntity self = persistUser(1_000_220);

        // When
        PagedResult<User> result = getUserRepository.findAllVisible(byNumber(0, 10), self.getId(), Set.of());

        // Then
        Assertions.assertEquals(List.of(self.getId()), ids(result));
    }

    @Test
    void findAllVisibleReturnsNobodyForNoCallerAndAnEmptySetOfCommunities() {
        persistUser(1_000_310);

        PagedResult<User> result = getUserRepository.findAllVisible(byNumber(0, 10), null, Set.of());

        Assertions.assertTrue(result.getItems().isEmpty());
    }

    private UserEntity persistUser(int number) {
        UserEntity user = UserMother.randomUserEntity();
        user.setNumber(number);
        return userRepository.save(user);
    }

    private void persistMembership(UserEntity user, Community community, boolean enabled) {
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);
        if (!enabled) {
            // No repository disables a membership yet, so the state is set on the entity directly
            CommunityMembershipEntity membership = communityMembershipJpaRepository
                    .findByUserIdAndCommunityId(user.getId(), community.getId()).orElseThrow();
            membership.setEnabled(false);
            communityMembershipJpaRepository.save(membership);
        }
    }

    private static PagedRequest byNumber(int page, int size) {
        return PagedRequest.of(page, size, List.of(new Order(Direction.ASC, "number")));
    }

    private static List<UUID> ids(PagedResult<User> result) {
        return result.getItems().stream().map(User::getId).toList();
    }
}
