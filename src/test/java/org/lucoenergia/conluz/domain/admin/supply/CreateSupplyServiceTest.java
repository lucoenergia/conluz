package org.lucoenergia.conluz.domain.admin.supply;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.UserNotFoundException;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyServiceImpl;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateSupplyServiceTest {

    private final GetUserRepository userRepository = mock(GetUserRepository.class);
    private final CreateSupplyRepository supplyRepository = mock(CreateSupplyRepository.class);
    private final CreateSupplyService createSupplyService = new CreateSupplyServiceImpl(supplyRepository, userRepository);

    @Test
    void testCreateSupplyWithUserPersonalIdWhenUserExist_success() {
        // arrange
        Supply expectedSupply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("code")
                .withAddress("address")
                .withPartitionCoefficient(1.0F)
                .withEnabled(Boolean.TRUE)
                .build();

        User user = UserMother.randomUser();
        when(userRepository.findByPersonalId(any(UserPersonalId.class))).thenReturn(Optional.of(user));
        when(supplyRepository.create(any(Supply.class), any(UserId.class), any())).thenReturn(expectedSupply);

        UUID communityId = UUID.randomUUID();

        // act
        Supply actualSupply = createSupplyService.create(expectedSupply, UserPersonalId.of(user.getPersonalId()),
                communityId);

        // assert
        assertNotNull(actualSupply);
        assertEquals(expectedSupply, actualSupply);
    }

    @Test
    void testCreateSupplyWithUserPersonalIdWhenUserNotExistThrowUserNotFoundException() {
        // arrange
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("code")
                .withAddress("address")
                .withPartitionCoefficient(1.0F)
                .withEnabled(Boolean.TRUE)
                .build();

        UUID communityId = UUID.randomUUID();

        when(userRepository.findByPersonalId(any(UserPersonalId.class))).thenReturn(Optional.empty());

        // act & assert
        assertThrows(UserNotFoundException.class, () -> createSupplyService.create(supply, UserPersonalId.of("123"),
                communityId));
    }
}