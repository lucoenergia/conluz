package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.shared.pagination.PagedRequest;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyMother;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DatadisSuppliesSyncServiceTest {

    private final GetSupplyRepository getSupplyRepository = Mockito.mock(GetSupplyRepository.class);
    private final UpdateSupplyRepository updateSupplyRepository = Mockito.mock(UpdateSupplyRepository.class);
    private final GetSupplyRepositoryDatadis getSupplyRepositoryDatadis = Mockito.mock(GetSupplyRepositoryDatadis.class);

    private final DatadisSuppliesSyncService service = new DatadisSuppliesSyncService(
            getSupplyRepository, updateSupplyRepository, getSupplyRepositoryDatadis
    );

    @Test
    void testSynchronizeZeroSupplies() {
        // Assemble
        List<Supply> supplies = new ArrayList<>();
        List<DatadisSupply> datadisSupplies = new ArrayList<>();

        Mockito.when(getSupplyRepository.count()).thenReturn(2L);
        Mockito.when(getSupplyRepository.findAll(any(PagedRequest.class)))
                .thenReturn(new PagedResult<>(supplies, 2, 2, 1, 0));

        Mockito.when(getSupplyRepositoryDatadis.getSupplies()).thenReturn(datadisSupplies);

        // Act
        service.synchronizeSupplies();

        // Assert
        verify(getSupplyRepository, times(1)).findAll(any(PagedRequest.class));
        verify(getSupplyRepositoryDatadis, times(1)).getSupplies();
        verify(updateSupplyRepository, times(0)).update(Mockito.any());
    }

    @Test
    void testSynchronizeSupplies() {
        // Assemble
        String cups1 = "ES4561237890F";
        String cups2 = "ES3216549870F";
        String cups3 = "ES1472583690F";
        List<Supply> supplies = Arrays.asList(
                SupplyMother.random().withCode(cups1).build(),
                SupplyMother.random().withCode(cups2).build(),
                SupplyMother.random().withCode(cups3).build()
        );
        List<DatadisSupply> datadisSupplies = Arrays.asList(
                DatadisSupplyMother.random(cups1).build(),
                DatadisSupplyMother.random("foo").build()
        );

        Mockito.when(getSupplyRepository.count()).thenReturn(3L);
        Mockito.when(getSupplyRepository.findAll(any(PagedRequest.class)))
                .thenReturn(new PagedResult<>(supplies, 3, 3, 1, 0));

        Mockito.when(getSupplyRepositoryDatadis.getSupplies()).thenReturn(datadisSupplies);

        // Act
        service.synchronizeSupplies();

        // Assert
        verify(getSupplyRepository, times(1)).findAll(any(PagedRequest.class));
        verify(getSupplyRepositoryDatadis, times(1)).getSupplies();
        verify(updateSupplyRepository, times(1)).update(Mockito.any());
    }
}