package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
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
    private final CommunityAccessGuard communityAccessGuard = Mockito.mock(CommunityAccessGuard.class);

    private final GetSupplyService service = new GetSupplyServiceImpl(repository, authService, communityAccessGuard);

    @Test
    void shouldReturnSupplyWhenUserCanRead() {
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
        when(communityAccessGuard.canReadSupply(supply)).thenReturn(true);

        Supply result = service.getById(supplyId);

        assertNotNull(result);
        assertEquals(supply, result);
        verify(repository).findById(supplyId);
        verify(communityAccessGuard).canReadSupply(supply);
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenCannotReadSupply() {
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
        when(communityAccessGuard.canReadSupply(supply)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getById(supplyId));

        verify(repository).findById(supplyId);
        verify(communityAccessGuard).canReadSupply(supply);
    }

    @Test
    void shouldThrowSupplyNotFoundExceptionWhenSupplyDoesNotExist() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());

        when(repository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getById(supplyId));

        verify(repository).findById(supplyId);
    }
}
