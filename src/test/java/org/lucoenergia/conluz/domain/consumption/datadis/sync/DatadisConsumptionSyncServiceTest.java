package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.mockito.Mockito.*;

@Transactional
class DatadisConsumptionSyncServiceTest extends BaseIntegrationTest {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository =
            Mockito.mock(GetDatadisConsumptionRepository.class);
    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository =
            Mockito.mock(PersistDatadisConsumptionRepository.class);
    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;

    private DatadisConsumptionSyncService service;

    @BeforeEach
    void setUp() {
        service = new DatadisConsumptionSyncService(getDatadisConsumptionRepository,
                getSupplyRepository, persistDatadisConsumptionRepository);
    }

    @Test
    void testSynchronizeConsumptionsValidDateFromNull() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supplyWithNullValidDateFrom = SupplyMother.random().withValidDateFrom(null).build();
        supplyWithNullValidDateFrom = createSupplyRepository.create(supplyWithNullValidDateFrom, UserId.of(user.getId()));
        Supply supplyWithNotNullValidDateFrom = SupplyMother.random()
                .withValidDateFrom(LocalDate.now().minusMonths(8)).build();
        supplyWithNotNullValidDateFrom = createSupplyRepository.create(supplyWithNotNullValidDateFrom, UserId.of(user.getId()));
        Supply supplyWithMoreThanOneYearValidDateFrom = SupplyMother.random()
                .withValidDateFrom(LocalDate.now().minusMonths(24)).build();
        supplyWithMoreThanOneYearValidDateFrom = createSupplyRepository.create(supplyWithMoreThanOneYearValidDateFrom, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        service.synchronizeConsumptions();

        // Then
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supplyWithNullValidDateFrom), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(8))
                .getHourlyConsumptionsByMonth(eq(supplyWithNotNullValidDateFrom), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(12))
                .getHourlyConsumptionsByMonth(eq(supplyWithMoreThanOneYearValidDateFrom), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(21)).persistConsumptions(anyList());
    }
}