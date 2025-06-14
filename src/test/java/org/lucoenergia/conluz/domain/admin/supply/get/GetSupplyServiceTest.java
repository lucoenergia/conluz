package org.lucoenergia.conluz.domain.admin.supply.get;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
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

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Transactional
class GetSupplyServiceTest extends BaseIntegrationTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private GetSupplyService getSupplyService;

    @Test
    void testFindAll() {
        // Assemble
        String userPersonalId = "987654321S";
        User user = UserMother.randomUserWithId(UUID.fromString("e7ab39cd-9250-40a9-b829-f11f65aae27d"));
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);

        String supplyCode = "ES002100823465";
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode(supplyCode)
                .withAddress(RandomStringUtils.random(20, true, true))
                .withPartitionCoefficient(new Random().nextFloat())
                .withEnabled(true)
                .withUser(user)
                .withName(RandomStringUtils.random(20, true, true))
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        // Act
        PagedResult<Supply> result = getSupplyService.findAll(PagedRequest.of(0, 10));

        // Assert
        assertEquals(1, result.getItems().size());
        assertEquals("ES002100823465", result.getItems().get(0).getCode());
        assertNotNull(result.getItems().get(0).getId());
        assertEquals(supply.getAddress(), result.getItems().get(0).getAddress());
        assertEquals(supply.getName(), result.getItems().get(0).getName());
        assertEquals(supply.getPartitionCoefficient(), result.getItems().get(0).getPartitionCoefficient());
        assertEquals(supply.getEnabled(), result.getItems().get(0).getEnabled());
        assertEquals("987654321S", result.getItems().get(0).getUser().getPersonalId());
    }
}