package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
class GetSupplyRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyRepositoryDatabase getSupplyRepositoryDatabase;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;

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
}
