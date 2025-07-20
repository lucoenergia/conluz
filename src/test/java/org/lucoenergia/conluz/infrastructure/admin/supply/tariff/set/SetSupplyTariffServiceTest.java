package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.set;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffRepository;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SetSupplyTariffService;
import org.lucoenergia.conluz.domain.admin.supply.tariff.SupplyAccessHelper;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetSupplyTariffServiceTest {

    private final SetSupplyTariffRepository repository = Mockito.mock(SetSupplyTariffRepository.class);
    private final GetSupplyRepository supplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final SupplyAccessHelper supplyAccessHelper = Mockito.mock(SupplyAccessHelper.class);

    private final SetSupplyTariffService service = new SetSupplyTariffServiceImpl(repository, supplyRepository,
            supplyAccessHelper);

    @Test
    void testSetTariff_Success() {
        UUID supplyId = UUID.randomUUID();
        Supply supply = mock(Supply.class);
        when(supply.getId()).thenReturn(supplyId);

        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(null)
                .withSupply(supply)
                .withValley(0.1)
                .withPeak(0.2)
                .withOffPeak(0.3)
                .build();

        when(supplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.of(supply));
        when(supplyAccessHelper.isAdminOrSupplyOwner(supply)).thenReturn(true);
        SupplyTariff savedTariff = new SupplyTariff.Builder()
                .withId(UUID.randomUUID())
                .withSupply(supply)
                .withValley(supplyTariff.getValley())
                .withPeak(supplyTariff.getPeak())
                .withOffPeak(supplyTariff.getOffPeak())
                .build();
        when(repository.save(any(SupplyTariff.class))).thenReturn(savedTariff);

        SupplyTariff result = service.setTariff(supplyTariff);

        assertNotNull(result.getId());
        assertEquals(supplyTariff.getSupply(), result.getSupply());
        assertEquals(supplyTariff.getValley(), result.getValley());
        assertEquals(supplyTariff.getPeak(), result.getPeak());
        assertEquals(supplyTariff.getOffPeak(), result.getOffPeak());
        verify(repository).save(any(SupplyTariff.class));
    }

    @Test
    void testSetTariff_SupplyNotFound() {
        UUID supplyId = UUID.randomUUID();
        Supply supply = mock(Supply.class);
        when(supply.getId()).thenReturn(supplyId);

        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(null)
                .withSupply(supply)
                .withValley(0.1)
                .withPeak(0.2)
                .withOffPeak(0.3)
                .build();

        when(supplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.empty());

        SupplyNotFoundException exception = assertThrows(SupplyNotFoundException.class,
                () -> service.setTariff(supplyTariff));

        assertEquals(SupplyId.of(supplyId), exception.getId());
        verify(repository, never()).save(any());
    }

    @Test
    void testSetTariff_AccessDenied() {
        UUID supplyId = UUID.randomUUID();
        Supply supply = mock(Supply.class);
        when(supply.getId()).thenReturn(supplyId);

        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(null)
                .withSupply(supply)
                .withValley(0.1)
                .withPeak(0.2)
                .withOffPeak(0.3)
                .build();

        when(supplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.of(supply));
        when(supplyAccessHelper.isAdminOrSupplyOwner(supply)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.setTariff(supplyTariff));

        verify(repository, never()).save(any());
    }

    @Test
    void testSetTariff_GenerateIdForNewEntity() {
        UUID supplyId = UUID.randomUUID();
        Supply supply = mock(Supply.class);
        when(supply.getId()).thenReturn(supplyId);

        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(null)
                .withSupply(supply)
                .withValley(0.1)
                .withPeak(0.2)
                .withOffPeak(0.3)
                .build();

        when(supplyRepository.findById(SupplyId.of(supplyId))).thenReturn(Optional.of(supply));
        when(supplyAccessHelper.isAdminOrSupplyOwner(supply)).thenReturn(true);

        SupplyTariff savedTariff = new SupplyTariff.Builder()
                .withId(UUID.randomUUID())
                .withSupply(supply)
                .withValley(supplyTariff.getValley())
                .withPeak(supplyTariff.getPeak())
                .withOffPeak(supplyTariff.getOffPeak())
                .build();
        when(repository.save(any(SupplyTariff.class))).thenReturn(savedTariff);

        SupplyTariff result = service.setTariff(supplyTariff);

        assertNotNull(result.getId());
        assertEquals(savedTariff.getId(), result.getId());
        verify(repository).save(any(SupplyTariff.class));
    }
}