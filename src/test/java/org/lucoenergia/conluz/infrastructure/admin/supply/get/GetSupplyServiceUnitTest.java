package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.admin.user.Role;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetSupplyServiceUnitTest {

    private final GetSupplyRepository repository = Mockito.mock(GetSupplyRepository.class);
    private final AuthService authService = Mockito.mock(AuthService.class);

    private final GetSupplyService service = new GetSupplyServiceImpl(repository, authService);

    @Test
    void shouldReturnSupplyWhenUserIsAdmin() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        Supply supply = new Supply.Builder()
                .withId(supplyId.getId())
                .withCode("SUP123")
                .withName("Test Supply")
                .withAddress("123 Test Street")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();

        User adminUser = new User();
        adminUser.setRole(Role.ADMIN);

        when(repository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(authService.getCurrentUser()).thenReturn(Optional.of(adminUser));

        Supply result = service.getById(supplyId);

        assertNotNull(result);
        assertEquals(supply, result);
        verify(repository).findById(supplyId);
        verify(authService).getCurrentUser();
    }

    @Test
    void shouldReturnSupplyWhenUserIsOwner() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setRole(Role.PARTNER);

        Supply supply = new Supply.Builder()
                .withId(supplyId.getId())
                .withCode("SUP123")
                .withName("Test Supply")
                .withAddress("123 Test Street")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .withUser(owner)
                .build();

        when(repository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(authService.getCurrentUser()).thenReturn(Optional.of(owner));

        Supply result = service.getById(supplyId);

        assertNotNull(result);
        assertEquals(supply, result);
        verify(repository).findById(supplyId);
        verify(authService).getCurrentUser();
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOrAdmin() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(Role.PARTNER);

        User owner = new User();
        owner.setId(UUID.randomUUID());

        Supply supply = new Supply.Builder()
                .withId(supplyId.getId())
                .withCode("SUP123")
                .withName("Test Supply")
                .withAddress("123 Test Street")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .withUser(owner)
                .build();

        when(repository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(authService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThrows(AccessDeniedException.class, () -> service.getById(supplyId));

        verify(repository).findById(supplyId);
        verify(authService).getCurrentUser();
    }

    @Test
    void shouldThrowSupplyNotFoundExceptionWhenSupplyDoesNotExist() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());

        when(repository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getById(supplyId));

        verify(repository).findById(supplyId);
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNoUserIsAuthenticated() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());

        Supply supply = new Supply.Builder()
                .withId(supplyId.getId())
                .withCode("SUP123")
                .withName("Test Supply")
                .withAddress("123 Test Street")
                .withPartitionCoefficient(1.0f)
                .withEnabled(true)
                .build();

        when(repository.findById(supplyId)).thenReturn(Optional.of(supply));
        when(authService.getCurrentUser()).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.getById(supplyId));

        verify(repository).findById(supplyId);
        verify(authService).getCurrentUser();
    }
}