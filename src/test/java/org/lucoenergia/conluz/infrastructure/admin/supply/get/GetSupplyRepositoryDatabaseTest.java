package org.lucoenergia.conluz.infrastructure.admin.supply.get;

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
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
}
