package org.lucoenergia.conluz.infrastructure.admin.supply.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetSupplyServiceImpl#getById}. Access control is enforced at the
 * controller via {@code @PreAuthorize("@communityAccessGuard.canReadSupply(#id)")}, so the
 * service only resolves the supply or throws when it does not exist.
 */
class GetSupplyServiceUnitTest {

    private final GetSupplyRepository repository = Mockito.mock(GetSupplyRepository.class);
    private final GetSupplyService service = new GetSupplyServiceImpl(repository);

    @Test
    void shouldReturnSupplyWhenExists() {
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

        Supply result = service.getById(supplyId);

        assertNotNull(result);
        assertEquals(supply, result);
        verify(repository).findById(supplyId);
    }

    @Test
    void shouldThrowSupplyNotFoundExceptionWhenSupplyDoesNotExist() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());

        when(repository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getById(supplyId));

        verify(repository).findById(supplyId);
    }
}
