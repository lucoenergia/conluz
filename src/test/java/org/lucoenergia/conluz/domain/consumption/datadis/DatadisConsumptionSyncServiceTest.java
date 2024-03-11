package org.lucoenergia.conluz.domain.consumption.datadis;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.pagination.PagedResult;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;

import static org.mockito.Mockito.*;

class DatadisConsumptionSyncServiceTest {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository = mock(GetDatadisConsumptionRepository.class);
    private final GetSupplyRepository getSupplyRepository = mock(GetSupplyRepository.class);
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository = mock(PersistDatadisConsumptionRepository.class);
    private final DatadisConsumptionSyncService datadisConsumptionSyncService = new DatadisConsumptionSyncService(
            getDatadisConsumptionRepository,
            getSupplyRepository,
            persistDatadisConsumptionRepository
    );

    @Test
    void testSynchronizeConsumptionsSuccessfully() {

        // Given
        User user = UserMother.randomUser();
        Supply supply = new Supply.Builder()
                .withCode(RandomStringUtils.random(20, true, true))
                .withUser(user)
                .withValidDateFrom(LocalDate.now().minusMonths(4))
                .build();

        when(getSupplyRepository.count()).thenReturn(1L);

        when(getSupplyRepository.findAll(any())).thenReturn(
                new PagedResult<>(Collections.singletonList(supply), 1, 1, 1, 0)
        );

        Consumption consumption = new Consumption();
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(Collections.singletonList(consumption));

        // When
        datadisConsumptionSyncService.synchronizeConsumptions();

        // Then
        verify(getSupplyRepository, times(1)).count();
        verify(getSupplyRepository, times(1)).findAll(any());

        verify(getDatadisConsumptionRepository, times(4)).getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(4)).persistConsumptions(Collections.singletonList(consumption));
    }

    @Test
    void testSynchronizeConsumptionsWithSupplyWithNullValidDate() {

        // Given
        User user = UserMother.randomUser();
        Supply supply = new Supply.Builder()
                .withCode(RandomStringUtils.random(20, true, true))
                .withUser(user)
                .build();

        when(getSupplyRepository.count()).thenReturn(1L);

        when(getSupplyRepository.findAll(any())).thenReturn(
                new PagedResult<>(Collections.singletonList(supply), 1, 1, 1, 0)
        );

        Consumption consumption = new Consumption();
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(Collections.singletonList(consumption));

        // When
        datadisConsumptionSyncService.synchronizeConsumptions();

        // Then
        verify(getSupplyRepository, times(1)).count();
        verify(getSupplyRepository, times(1)).findAll(any());

        verify(getDatadisConsumptionRepository, times(1)).getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(1)).persistConsumptions(Collections.singletonList(consumption));
    }

    @Test
    void testSynchronizeConsumptionsSuccessfullyWithValidDateOlderThanAYear() {

        // Given
        User user = UserMother.randomUser();
        Supply supply = new Supply.Builder()
                .withCode(RandomStringUtils.random(20, true, true))
                .withUser(user)
                .withValidDateFrom(LocalDate.now().minusMonths(20))
                .build();

        when(getSupplyRepository.count()).thenReturn(1L);

        when(getSupplyRepository.findAll(any())).thenReturn(
                new PagedResult<>(Collections.singletonList(supply), 1, 1, 1, 0)
        );

        Consumption consumption = new Consumption();
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt()))
                .thenReturn(Collections.singletonList(consumption));

        // When
        datadisConsumptionSyncService.synchronizeConsumptions();

        // Then
        verify(getSupplyRepository, times(1)).count();
        verify(getSupplyRepository, times(1)).findAll(any());

        verify(getDatadisConsumptionRepository, times(12)).getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(12)).persistConsumptions(Collections.singletonList(consumption));
    }
}
