package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.GetSupplyTariffRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.GetSupplyTariffService;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyAccessHelper;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetSupplyTariffServiceTest {

    private final GetSupplyTariffRepository getSupplyTariffRepository = Mockito.mock(GetSupplyTariffRepository.class);
    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final SupplyAccessHelper supplyAccessHelper = Mockito.mock(SupplyAccessHelper.class);

    private final GetSupplyTariffService service = new GetSupplyTariffServiceImpl(getSupplyTariffRepository,
            getSupplyRepository, supplyAccessHelper);

    @Test
    void testGetTariffBySupplyId_SupplyNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.empty());

        assertThrows(SupplyNotFoundException.class, () -> service.getTariffBySupplyId(supplyId));
        verify(getSupplyRepository, times(1)).findById(supplyId);
        verifyNoInteractions(getSupplyTariffRepository);
        verifyNoInteractions(supplyAccessHelper);
    }

    @Test
    void testGetTariffBySupplyId_AccessDenied() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        Supply mockSupply = mock(Supply.class);

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(mockSupply));
        when(supplyAccessHelper.isAdminOrSupplyOwner(mockSupply)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getTariffBySupplyId(supplyId));
        verify(getSupplyRepository, times(1)).findById(supplyId);
        verify(supplyAccessHelper, times(1)).isAdminOrSupplyOwner(mockSupply);
        verifyNoInteractions(getSupplyTariffRepository);
    }

    @Test
    void testGetTariffBySupplyId_TariffNotFound() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        Supply mockSupply = mock(Supply.class);

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(mockSupply));
        when(supplyAccessHelper.isAdminOrSupplyOwner(mockSupply)).thenReturn(true);
        when(getSupplyTariffRepository.findBySupplyId(supplyId)).thenReturn(Optional.empty());

        Optional<SupplyTariff> result = service.getTariffBySupplyId(supplyId);

        assertTrue(result.isEmpty());
        verify(getSupplyRepository, times(1)).findById(supplyId);
        verify(supplyAccessHelper, times(1)).isAdminOrSupplyOwner(mockSupply);
        verify(getSupplyTariffRepository, times(1)).findBySupplyId(supplyId);
    }

    @Test
    void testGetTariffBySupplyId_Success() {
        SupplyId supplyId = SupplyId.of(UUID.randomUUID());
        Supply mockSupply = mock(Supply.class);
        SupplyTariff mockTariff = mock(SupplyTariff.class);

        when(getSupplyRepository.findById(supplyId)).thenReturn(Optional.of(mockSupply));
        when(supplyAccessHelper.isAdminOrSupplyOwner(mockSupply)).thenReturn(true);
        when(getSupplyTariffRepository.findBySupplyId(supplyId)).thenReturn(Optional.of(mockTariff));

        Optional<SupplyTariff> result = service.getTariffBySupplyId(supplyId);

        assertTrue(result.isPresent());
        assertEquals(mockTariff, result.get());
        verify(getSupplyRepository, times(1)).findById(supplyId);
        verify(supplyAccessHelper, times(1)).isAdminOrSupplyOwner(mockSupply);
        verify(getSupplyTariffRepository, times(1)).findBySupplyId(supplyId);
    }
}