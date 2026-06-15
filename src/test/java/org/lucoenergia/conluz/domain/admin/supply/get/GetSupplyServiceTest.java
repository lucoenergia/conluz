package org.lucoenergia.conluz.domain.admin.supply.get;

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
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class GetSupplyServiceTest extends BaseIntegrationTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private GetSupplyService getSupplyService;

    @Test
    void findByCommunityReturnsAllSuppliesOfTheCommunityRegardlessOfOwner() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User ownerOne = createUserRepository.create(UserMother.randomUser());
        User ownerTwo = createUserRepository.create(UserMother.randomUser());

        Supply supplyOne = createSupplyRepository.create(SupplyMother.random(ownerOne).build(),
                UserId.of(ownerOne.getId()), community.getId());
        Supply supplyTwo = createSupplyRepository.create(SupplyMother.random(ownerTwo).build(),
                UserId.of(ownerTwo.getId()), community.getId());

        // When
        PagedResult<Supply> result = getSupplyService.findByCommunity(PagedRequest.of(0, 20), community.getId());

        // Then
        assertEquals(2, result.getTotalElements());
        List<UUID> ids = result.getItems().stream().map(Supply::getId).collect(Collectors.toList());
        assertTrue(ids.contains(supplyOne.getId()));
        assertTrue(ids.contains(supplyTwo.getId()));
    }

    @Test
    void findByCommunityExcludesSuppliesFromOtherCommunities() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User owner = createUserRepository.create(UserMother.randomUser());

        Supply supplyInCommunity = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), otherCommunity.getId());

        // When
        PagedResult<Supply> result = getSupplyService.findByCommunity(PagedRequest.of(0, 20), community.getId());

        // Then
        assertEquals(1, result.getTotalElements());
        List<UUID> ids = result.getItems().stream().map(Supply::getId).collect(Collectors.toList());
        assertTrue(ids.contains(supplyInCommunity.getId()));
        assertFalse(ids.contains(supplyInOtherCommunity.getId()));
    }

    @Test
    void findByCommunityReturnsEmptyResultWhenCommunityHasNoSupplies() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        // When
        PagedResult<Supply> result = getSupplyService.findByCommunity(PagedRequest.of(0, 20), community.getId());

        // Then
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void findByCommunityRespectsPagination() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User owner = createUserRepository.create(UserMother.randomUser());

        for (int i = 0; i < 3; i++) {
            createSupplyRepository.create(SupplyMother.random(owner).build(),
                    UserId.of(owner.getId()), community.getId());
        }

        // When
        PagedResult<Supply> firstPage = getSupplyService.findByCommunity(PagedRequest.of(0, 2), community.getId());
        PagedResult<Supply> secondPage = getSupplyService.findByCommunity(PagedRequest.of(1, 2), community.getId());

        // Then
        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(2, firstPage.getItems().size());
        assertEquals(1, secondPage.getItems().size());
    }

    @Test
    void findByOwnerAndCommunityReturnsOnlySuppliesOwnedByTheGivenUserInThatCommunity() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User owner = createUserRepository.create(UserMother.randomUser());
        User otherOwner = createUserRepository.create(UserMother.randomUser());

        Supply ownerSupply = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), community.getId());
        Supply otherOwnerSupply = createSupplyRepository.create(SupplyMother.random(otherOwner).build(),
                UserId.of(otherOwner.getId()), community.getId());

        // When
        PagedResult<Supply> result = getSupplyService.findByOwnerAndCommunity(PagedRequest.of(0, 20),
                UserId.of(owner.getId()), community.getId());

        // Then
        assertEquals(1, result.getTotalElements());
        List<UUID> ids = result.getItems().stream().map(Supply::getId).collect(Collectors.toList());
        assertTrue(ids.contains(ownerSupply.getId()));
        assertFalse(ids.contains(otherOwnerSupply.getId()));
    }

    @Test
    void findByOwnerAndCommunityExcludesSuppliesOwnedByTheUserInOtherCommunities() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community otherCommunity = createCommunityRepository.create(CommunityMother.random().build());

        User owner = createUserRepository.create(UserMother.randomUser());

        Supply supplyInCommunity = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), community.getId());
        Supply supplyInOtherCommunity = createSupplyRepository.create(SupplyMother.random(owner).build(),
                UserId.of(owner.getId()), otherCommunity.getId());

        // When
        PagedResult<Supply> result = getSupplyService.findByOwnerAndCommunity(PagedRequest.of(0, 20),
                UserId.of(owner.getId()), community.getId());

        // Then
        assertEquals(1, result.getTotalElements());
        List<UUID> ids = result.getItems().stream().map(Supply::getId).collect(Collectors.toList());
        assertTrue(ids.contains(supplyInCommunity.getId()));
        assertFalse(ids.contains(supplyInOtherCommunity.getId()));
    }

    @Test
    void findByOwnerAndCommunityReturnsEmptyResultWhenUserOwnsNoSuppliesInTheCommunity() {
        // Given
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        User owner = createUserRepository.create(UserMother.randomUser());
        User otherOwner = createUserRepository.create(UserMother.randomUser());

        // Only the other user owns a supply in the community.
        createSupplyRepository.create(SupplyMother.random(otherOwner).build(),
                UserId.of(otherOwner.getId()), community.getId());

        // When
        PagedResult<Supply> result = getSupplyService.findByOwnerAndCommunity(PagedRequest.of(0, 20),
                UserId.of(owner.getId()), community.getId());

        // Then
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getItems().isEmpty());
    }
}
