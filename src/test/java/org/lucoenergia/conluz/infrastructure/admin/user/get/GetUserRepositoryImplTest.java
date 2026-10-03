package org.lucoenergia.conluz.infrastructure.admin.user.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.domain.shared.pagination.Direction;
import org.lucoenergia.conluz.domain.shared.pagination.Order;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
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
class GetUserRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;


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
        communityMembershipJpaRepository.save(new CommunityMembershipEntity.Builder()
                .withId(UUID.randomUUID())
                .withUser(user)
                .withCommunity(communityJpaRepository.findById(community.getId()).orElseThrow())
                .withRole(CommunityRole.COMMUNITY_MEMBER)
                .withEnabled(enabled)
                .build());
    }

    private static PagedRequest byNumber(int page, int size) {
        return PagedRequest.of(page, size, List.of(new Order(Direction.ASC, "number")));
    }

    private static List<UUID> ids(PagedResult<User> result) {
        return result.getItems().stream().map(User::getId).toList();
    }
}
