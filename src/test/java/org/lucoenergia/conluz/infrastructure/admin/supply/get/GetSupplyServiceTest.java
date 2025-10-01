package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetSupplyServiceTest {

    private final GetSupplyRepository repository = Mockito.mock(GetSupplyRepository.class);
    private final GetSupplyService service = new GetSupplyServiceImpl(repository, null);

    @Test
    void getByUserIdWithAuthorization_shouldReturnSuppliesWhenUserIsAdmin() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        UserId userIdValue = UserId.of(userId);
        UserId requestingUserIdValue = UserId.of(requestingUserId);

        User user = UserMother.randomUserWithId(userId);
        Supply supply1 = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(user)
                .withName("Supply 1")
                .withAddress("Address 1")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();
        Supply supply2 = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES002")
                .withUser(user)
                .withName("Supply 2")
                .withAddress("Address 2")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();

        List<Supply> expectedSupplies = Arrays.asList(supply1, supply2);
        when(repository.findByUserId(userIdValue)).thenReturn(expectedSupplies);

        // When
        List<Supply> result = service.getByUserId(userIdValue, requestingUserIdValue, true);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedSupplies, result);
        verify(repository).findByUserId(userIdValue);
    }

    @Test
    void getByUserIdWithAuthorization_shouldReturnSuppliesWhenUserRequestsOwnSupplies() {
        // Given
        UUID userId = UUID.randomUUID();
        UserId userIdValue = UserId.of(userId);

        User user = UserMother.randomUserWithId(userId);
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode("ES001")
                .withUser(user)
                .withName("Supply 1")
                .withAddress("Address 1")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();

        List<Supply> expectedSupplies = Arrays.asList(supply);
        when(repository.findByUserId(userIdValue)).thenReturn(expectedSupplies);

        // When
        List<Supply> result = service.getByUserId(userIdValue, userIdValue, false);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(expectedSupplies, result);
        verify(repository).findByUserId(userIdValue);
    }

    @Test
    void getByUserIdWithAuthorization_shouldThrowAccessDeniedWhenNonAdminRequestsOtherUserSupplies() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID requestingUserId = UUID.randomUUID();
        UserId userIdValue = UserId.of(userId);
        UserId requestingUserIdValue = UserId.of(requestingUserId);

        // When & Then
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            service.getByUserId(userIdValue, requestingUserIdValue, false);
        });

        assertEquals("You do not have permission to access supplies for this user", exception.getMessage());
        verify(repository, never()).findByUserId(any());
    }
}
