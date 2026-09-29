package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyResponse;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.SupplyCapabilitiesResponse;
import org.lucoenergia.conluz.infrastructure.admin.community.access.capability.UserCapabilitiesResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.UUID;

@Transactional
class GetSupplyRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyRepositoryDatabase getSupplyRepositoryDatabase;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllReturnsZero() {
        // Given

        // When
        List<Supply> result = getSupplyRepositoryDatabase.findAll();

        // Then
        Assertions.assertEquals(0, result.size());
    }

    @Test
    void findAllReturnsExpectedPagedResult() {
        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        final Supply supplyOne = createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
        final Supply supplyTwo = createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
        final Supply supplyThree = createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));

        // When
        List<Supply> result = getSupplyRepositoryDatabase.findAll();

        // Then
        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.contains(supplyOne));
        Assertions.assertTrue(result.contains(supplyTwo));
        Assertions.assertTrue(result.contains(supplyThree));
    }

    @Test
    void countReturnsZeroWhenThereAreNoSupplies() {
        // When
        long result = getSupplyRepositoryDatabase.count();

        // Then
        Assertions.assertEquals(0, result);
    }

    @Test
    void countReturnsNumberOfPersistedSupplies() {
        // Given
        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));

        // When
        long result = getSupplyRepositoryDatabase.count();

        // Then
        Assertions.assertEquals(2, result);
    }

    @Test
    void findByIdReturnsTheSupplyWhenItExists() {
        // Given
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));

        // When
        Optional<Supply> result = getSupplyRepositoryDatabase.findById(SupplyId.of(supply.getId()));

        // Then
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(supply, result.get());
    }

    @Test
    void findByIdReturnsEmptyWhenTheSupplyDoesNotExist() {
        // When
        Optional<Supply> result = getSupplyRepositoryDatabase.findById(SupplyId.of(UUID.randomUUID()));

        // Then
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void findByCodeReturnsTheSupplyWhenItExists() {
        // Given
        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));

        // When
        Optional<Supply> result = getSupplyRepositoryDatabase.findByCode(SupplyCode.of(supply.getCode()));

        // Then
        Assertions.assertTrue(result.isPresent());
        Assertions.assertEquals(supply, result.get());
    }

    @Test
    void findByCodeReturnsEmptyWhenNoSupplyMatchesTheCode() {
        // When
        Optional<Supply> result = getSupplyRepositoryDatabase.findByCode(SupplyCode.of("non-existent-code"));

        // Then
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void findAllPagedReturnsRequestedPage() {
        // Given
        User user = createUserRepository.create(UserMother.randomUser());
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()));

        // When
        PagedResult<Supply> firstPage = getSupplyRepositoryDatabase.findAll(PagedRequest.of(0, 2));
        PagedResult<Supply> secondPage = getSupplyRepositoryDatabase.findAll(PagedRequest.of(1, 2));

        // Then
        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getTotalPages());
        Assertions.assertEquals(2, firstPage.getItems().size());
        Assertions.assertEquals(1, secondPage.getItems().size());
    }

    @Test
    void findByUserIdReturnsOnlySuppliesOwnedByThatUser() {
        // Given
        User owner = createUserRepository.create(UserMother.randomUser());
        User otherUser = createUserRepository.create(UserMother.randomUser());

        Supply ownedOne = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()));
        Supply ownedTwo = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()));
        Supply otherUsersSupply = createSupplyRepository.create(SupplyMother.random(otherUser).build(),
                UserId.of(otherUser.getId()));

        // When
        List<Supply> result = getSupplyRepositoryDatabase.findByUserId(UserId.of(owner.getId()));

        // Then
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.contains(ownedOne));
        Assertions.assertTrue(result.contains(ownedTwo));
        Assertions.assertFalse(result.contains(otherUsersSupply));
    }

    @Test
    void findByUserIdReturnsEmptyListWhenUserOwnsNoSupplies() {
        // Given
        User user = createUserRepository.create(UserMother.randomUser());

        // When
        List<Supply> result = getSupplyRepositoryDatabase.findByUserId(UserId.of(user.getId()));

        // Then
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void findByCommunityReturnsOnlySuppliesOfThatCommunity() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User user = createUserRepository.create(UserMother.randomUser());

        Supply supplyOne = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyTwo = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), otherCommunity.getId());

        // When
        PagedResult<Supply> result = getSupplyRepositoryDatabase.findByCommunity(PagedRequest.of(0, 10),
                community.getId());

        // Then
        Assertions.assertEquals(2, result.getTotalElements());
        Assertions.assertEquals(2, result.getItems().size());
        Assertions.assertTrue(result.getItems().contains(supplyOne));
        Assertions.assertTrue(result.getItems().contains(supplyTwo));
        Assertions.assertFalse(result.getItems().contains(supplyInOtherCommunity));
    }

    @Test
    void findByCommunityReturnsRequestedPage() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUserRepository.create(UserMother.randomUser());

        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()), community.getId());
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()), community.getId());
        createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()), community.getId());

        // When
        PagedResult<Supply> firstPage = getSupplyRepositoryDatabase.findByCommunity(PagedRequest.of(0, 2),
                community.getId());
        PagedResult<Supply> secondPage = getSupplyRepositoryDatabase.findByCommunity(PagedRequest.of(1, 2),
                community.getId());

        // Then
        Assertions.assertEquals(3, firstPage.getTotalElements());
        Assertions.assertEquals(2, firstPage.getTotalPages());
        Assertions.assertEquals(2, firstPage.getItems().size());
        Assertions.assertEquals(1, secondPage.getItems().size());
    }

    @Test
    void findByCommunityReturnsEmptyResultWhenCommunityHasNoSupplies() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        // When
        PagedResult<Supply> result = getSupplyRepositoryDatabase.findByCommunity(PagedRequest.of(0, 10),
                community.getId());

        // Then
        Assertions.assertEquals(0, result.getTotalElements());
        Assertions.assertTrue(result.getItems().isEmpty());
    }

    @Test
    void findByOwnerAndCommunityReturnsOnlySuppliesOwnedByThatUserInThatCommunity() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User owner = createUserRepository.create(UserMother.randomUser());
        User otherUser = createUserRepository.create(UserMother.randomUser());

        Supply ownedInCommunity = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), community.getId());
        Supply ownedInOtherCommunity = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), otherCommunity.getId());
        Supply otherUsersSupplyInCommunity = createSupplyRepository.create(SupplyMother.random(otherUser).build(),
                UserId.of(otherUser.getId()), community.getId());

        // When
        PagedResult<Supply> result = getSupplyRepositoryDatabase.findByOwnerAndCommunity(PagedRequest.of(0, 10),
                UserId.of(owner.getId()), community.getId());

        // Then
        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(1, result.getItems().size());
        Assertions.assertTrue(result.getItems().contains(ownedInCommunity));
        Assertions.assertFalse(result.getItems().contains(ownedInOtherCommunity));
        Assertions.assertFalse(result.getItems().contains(otherUsersSupplyInCommunity));
    }

    @Test
    void findByOwnerAndCommunityReturnsEmptyResultWhenUserOwnsNoSuppliesInThatCommunity() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUserRepository.create(UserMother.randomUser());

        // When
        PagedResult<Supply> result = getSupplyRepositoryDatabase.findByOwnerAndCommunity(PagedRequest.of(0, 10),
                UserId.of(user.getId()), community.getId());

        // Then
        Assertions.assertEquals(0, result.getTotalElements());
        Assertions.assertTrue(result.getItems().isEmpty());
    }

    @Test
    void findAllByCommunityIdReturnsOnlySuppliesOfThatCommunity() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User user = createUserRepository.create(UserMother.randomUser());

        Supply supplyOne = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyTwo = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), otherCommunity.getId());

        // When
        List<Supply> result = getSupplyRepositoryDatabase.findAllByCommunityId(community.getId());

        // Then
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.contains(supplyOne));
        Assertions.assertTrue(result.contains(supplyTwo));
        Assertions.assertFalse(result.contains(supplyInOtherCommunity));
    }

    /**
     * The community reaches the response as a fully materialised domain object, so building the
     * response issues nothing. Mirrors the equivalent guarantee asserted for PlantResponse.
     */
    @Test
    void mappingSuppliesToSupplyResponseAddsNoAdditionalQueriesToExposeTheOwningCommunity() {
        User user = createUserRepository.create(UserMother.randomUser());
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        for (int i = 0; i < 3; i++) {
            createSupplyRepository.create(SupplyMother.random(user).build(), UserId.of(user.getId()),
                    community.getId());
        }
        entityManager.flush();
        entityManager.clear();

        List<Supply> supplies = getSupplyRepositoryDatabase.findByUserId(UserId.of(user.getId()));

        // Fixed capabilities: this asserts what the response constructor costs, not the assemblers,
        // which ListEndpointQueryCountTest covers.
        SupplyCapabilitiesResponse capabilities = SupplyCapabilitiesResponse.builder().build();
        UserCapabilitiesResponse ownerCapabilities = UserCapabilitiesResponse.builder().build();

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<SupplyResponse> responses = supplies.stream()
                .map(supply -> new SupplyResponse(supply, capabilities, ownerCapabilities))
                .toList();

        Assertions.assertEquals(0, statistics.getPrepareStatementCount(),
                "constructing SupplyResponse must not issue further queries: the community is already resolved");
        for (SupplyResponse response : responses) {
            Assertions.assertEquals(community.getId(), response.getCommunity().getId());
            Assertions.assertEquals(community.getName(), response.getCommunity().getName());
        }
    }

    /**
     * A differential assertion: both arms hold the same number of supplies owned by one user, and
     * differ only in how many communities those supplies span. Were the community resolved by lazy
     * initialisation, the two-community arm would cost one statement more. The absolute count is
     * deliberately not asserted -- it also covers per-supply loads unrelated to the community.
     */
    @Test
    void findByUserIdResolvesTheCommunityWithoutAQueryPerCommunity() {
        Community communityOne = createCommunityRepository.create(CommunityMother.random().build());
        Community communityTwo = createCommunityRepository.create(CommunityMother.random().build());

        User singleCommunityOwner = createUserRepository.create(UserMother.randomUser());
        User twoCommunityOwner = createUserRepository.create(UserMother.randomUser());

        for (int i = 0; i < 4; i++) {
            createSupplyRepository.create(SupplyMother.random(singleCommunityOwner).build(),
                    UserId.of(singleCommunityOwner.getId()), communityOne.getId());
            createSupplyRepository.create(SupplyMother.random(twoCommunityOwner).build(),
                    UserId.of(twoCommunityOwner.getId()), i % 2 == 0 ? communityOne.getId() : communityTwo.getId());
        }
        entityManager.flush();

        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        entityManager.clear();
        statistics.clear();
        List<Supply> singleCommunitySupplies = getSupplyRepositoryDatabase.findByUserId(
                UserId.of(singleCommunityOwner.getId()));
        long singleCommunityStatements = statistics.getPrepareStatementCount();

        entityManager.clear();
        statistics.clear();
        List<Supply> twoCommunitySupplies = getSupplyRepositoryDatabase.findByUserId(
                UserId.of(twoCommunityOwner.getId()));
        long twoCommunityStatements = statistics.getPrepareStatementCount();

        Assertions.assertEquals(4, singleCommunitySupplies.size());
        Assertions.assertEquals(4, twoCommunitySupplies.size());
        Assertions.assertEquals(singleCommunityStatements, twoCommunityStatements,
                "spanning a second community must not cost an extra query");
        Assertions.assertTrue(twoCommunitySupplies.stream().allMatch(supply -> supply.getCommunity() != null));
    }

    /**
     * Every association SupplyEntityMapper traverses is fetched with the supplies, so listing a
     * community costs the same whether it holds two supplies or six. Asserting flatness rather than
     * an absolute count keeps the test readable and independent of how many statements the paged
     * read itself needs.
     */
    @Test
    void findByCommunityResolvesEverySupplyWithoutAQueryPerSupply() {
        long twoSupplies = statementsToListCommunity(2);
        long sixSupplies = statementsToListCommunity(6);

        Assertions.assertEquals(twoSupplies, sixSupplies,
                "listing a community must not cost a query per supply");
    }

    /**
     * The same guarantee for the unpaginated read behind the scheduled jobs, which iterate every
     * supply and so pay a per-supply query in full.
     */
    @Test
    void findAllResolvesEverySupplyWithoutAQueryPerSupply() {
        long twoSupplies = statementsToListAll(2);
        long sixSupplies = statementsToListAll(6);

        Assertions.assertEquals(twoSupplies, sixSupplies,
                "listing every supply must not cost a query per supply");
    }

    private long statementsToListCommunity(int supplies) {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        createSuppliesWithDistinctOwners(supplies, community);

        return measure(() -> getSupplyRepositoryDatabase.findByCommunity(PagedRequest.of(0, 50),
                community.getId()).getItems().size());
    }

    private long statementsToListAll(int supplies) {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        createSuppliesWithDistinctOwners(supplies, community);

        return measure(() -> getSupplyRepositoryDatabase.findAllByCommunityId(community.getId()).size());
    }

    /**
     * Distinct owners on purpose: a shared owner would be resolved once by the persistence context
     * and hide a per-supply load.
     */
    private void createSuppliesWithDistinctOwners(int supplies, Community community) {
        for (int i = 0; i < supplies; i++) {
            User owner = createUserRepository.create(UserMother.randomUser());
            createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                    community.getId());
        }
        entityManager.flush();
    }

    private long measure(Supplier<Integer> read) {
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        statistics.clear();

        read.get();

        return statistics.getPrepareStatementCount();
    }
}
