package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.sync.DatadisConsumptionSyncServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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
        service = new DatadisConsumptionSyncServiceImpl(getDatadisConsumptionRepository,
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
        Supply supplyWithoutDistributorCode = SupplyMother.random()
                .withDistributorCode(null)
                .withValidDateFrom(LocalDate.now().minusMonths(24)).build();
        supplyWithoutDistributorCode = createSupplyRepository.create(supplyWithoutDistributorCode, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(mock(DatadisConsumption.class));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.now().minusYears(1).withDayOfMonth(1);
        LocalDate endDate = LocalDate.now();
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(13))
                .getHourlyConsumptionsByMonth(eq(supplyWithNullValidDateFrom), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(13))
                .getHourlyConsumptionsByMonth(eq(supplyWithNotNullValidDateFrom), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(13))
                .getHourlyConsumptionsByMonth(eq(supplyWithMoreThanOneYearValidDateFrom), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(0))
                .getHourlyConsumptionsByMonth(eq(supplyWithoutDistributorCode), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(39)).persistHourlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsForSingleMonth() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST123")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(
                ConsumptionMother.random(),
                ConsumptionMother.random(),
                ConsumptionMother.random()
        );
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 29);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.FEBRUARY), eq(2024));
        verify(persistDatadisConsumptionRepository, times(1)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsForMultipleMonths() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST456")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 3, 1);
        LocalDate endDate = LocalDate.of(2024, 5, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(3))
                .getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.MARCH), eq(2024));
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.APRIL), eq(2024));
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.MAY), eq(2024));
        verify(persistDatadisConsumptionRepository, times(3)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsForFullYear() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST789")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(12))
                .getHourlyConsumptionsByMonth(eq(supply), any(Month.class), eq(2024));
        verify(persistDatadisConsumptionRepository, times(12)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsAcrossYears() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DISTXY")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2023, 11, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 28);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(4))
                .getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.NOVEMBER), eq(2023));
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.DECEMBER), eq(2023));
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.JANUARY), eq(2024));
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.FEBRUARY), eq(2024));
        verify(persistDatadisConsumptionRepository, times(4)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsWithPartialData() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST002")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> januaryConsumptions = List.of(
                ConsumptionMother.random(),
                ConsumptionMother.random(),
                ConsumptionMother.random(),
                ConsumptionMother.random(),
                ConsumptionMother.random()
        );
        List<DatadisConsumption> marchConsumptions = List.of(
                ConsumptionMother.random(),
                ConsumptionMother.random(),
                ConsumptionMother.random()
        );

        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), eq(Month.JANUARY), anyInt()))
                .thenReturn(januaryConsumptions);
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), eq(Month.FEBRUARY), anyInt()))
                .thenReturn(List.of());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), eq(Month.MARCH), anyInt()))
                .thenReturn(marchConsumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(3))
                .getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(2)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsWithExceptionContinuesProcessing() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST003")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());

        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), eq(Month.JANUARY), anyInt()))
                .thenReturn(consumptions);
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), eq(Month.FEBRUARY), anyInt()))
                .thenThrow(new DatadisSupplyConfigurationException("Test exception for February"));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply), eq(Month.MARCH), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(3))
                .getHourlyConsumptionsByMonth(eq(supply), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(2)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsMultipleSuppliesWithException() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributorCode("SUPPLY1")
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributorCode("SUPPLY2")
                .build();
        supply2 = createSupplyRepository.create(supply2, UserId.of(user.getId()));

        Supply supply3 = SupplyMother.random()
                .withDistributorCode("SUPPLY3")
                .build();
        supply3 = createSupplyRepository.create(supply3, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());

        // Supply 1: Success for both months
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply1), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // Supply 2: Exception for January, success for February
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply2), eq(Month.JANUARY), anyInt()))
                .thenThrow(new DatadisSupplyConfigurationException("Test exception for supply 2 January"));
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply2), eq(Month.FEBRUARY), anyInt()))
                .thenReturn(consumptions);

        // Supply 3: Success for both months
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(eq(supply3), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 28);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(2))
                .getHourlyConsumptionsByMonth(eq(supply1), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(2))
                .getHourlyConsumptionsByMonth(eq(supply2), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(2))
                .getHourlyConsumptionsByMonth(eq(supply3), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(5)).persistHourlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsWithNoSupplies() {

        // Given
        // No supplies created

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, never())
                .getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, never()).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, never()).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, never()).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsAllSuppliesWithoutDistributorCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributorCode(null)
                .build();
        createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributorCode(null)
                .build();
        createSupplyRepository.create(supply2, UserId.of(user.getId()));

        Supply supply3 = SupplyMother.random()
                .withDistributorCode(null)
                .build();
        createSupplyRepository.create(supply3, UserId.of(user.getId()));

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 3, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, never())
                .getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, never()).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, never()).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, never()).persistYearlyConsumptions(anyList());
    }

    @Test
    void testMonthlyAggregationCalculatesCorrectSums() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("AGGTEST")
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        DatadisConsumption hour1 = new DatadisConsumption();
        hour1.setCups("ES1234567890");
        hour1.setDate("2024/01");
        hour1.setTime("01:00");
        hour1.setObtainMethod("Real");
        hour1.setConsumptionKWh(10.5f);
        hour1.setSurplusEnergyKWh(2.3f);
        hour1.setGenerationEnergyKWh(5.1f);
        hour1.setSelfConsumptionEnergyKWh(3.2f);

        DatadisConsumption hour2 = new DatadisConsumption();
        hour2.setCups("ES1234567890");
        hour2.setDate("2024/01");
        hour2.setTime("02:00");
        hour2.setObtainMethod("Real");
        hour2.setConsumptionKWh(8.2f);
        hour2.setSurplusEnergyKWh(1.8f);
        hour2.setGenerationEnergyKWh(4.5f);
        hour2.setSelfConsumptionEnergyKWh(2.7f);

        DatadisConsumption hour3 = new DatadisConsumption();
        hour3.setCups("ES1234567890");
        hour3.setDate("2024/01");
        hour3.setTime("03:00");
        hour3.setObtainMethod("Real");
        hour3.setConsumptionKWh(12.1f);
        hour3.setSurplusEnergyKWh(3.1f);
        hour3.setGenerationEnergyKWh(6.2f);
        hour3.setSelfConsumptionEnergyKWh(4.0f);

        List<DatadisConsumption> hourlyConsumptions = List.of(hour1, hour2, hour3);

        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(hourlyConsumptions);

        ArgumentCaptor<List<DatadisConsumption>> monthlyCaptor = ArgumentCaptor.forClass(List.class);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(persistDatadisConsumptionRepository).persistMonthlyConsumptions(monthlyCaptor.capture());

        List<DatadisConsumption> monthlyAggregated = monthlyCaptor.getValue();
        assertEquals(1, monthlyAggregated.size());

        DatadisConsumption monthly = monthlyAggregated.get(0);
        assertEquals(30.8f, monthly.getConsumptionKWh(), 0.01f);
        assertEquals(7.2f, monthly.getSurplusEnergyKWh(), 0.01f);
        assertEquals(15.8f, monthly.getGenerationEnergyKWh(), 0.01f);
        assertEquals(9.9f, monthly.getSelfConsumptionEnergyKWh(), 0.01f);
        assertEquals("ES1234567890", monthly.getCups());
        assertEquals("2024/01", monthly.getDate());
        assertEquals("Real", monthly.getObtainMethod());
    }

    @Test
    void testAggregationHandlesNullValuesCorrectly() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("NULLTEST")
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        DatadisConsumption hour1 = new DatadisConsumption();
        hour1.setCups("ES1234567890");
        hour1.setDate("2024/01");
        hour1.setTime("01:00");
        hour1.setObtainMethod("Real");
        hour1.setConsumptionKWh(10.0f);
        hour1.setSurplusEnergyKWh(null);
        hour1.setGenerationEnergyKWh(5.0f);
        hour1.setSelfConsumptionEnergyKWh(3.0f);

        DatadisConsumption hour2 = new DatadisConsumption();
        hour2.setCups("ES1234567890");
        hour2.setDate("2024/01");
        hour2.setTime("02:00");
        hour2.setObtainMethod("Real");
        hour2.setConsumptionKWh(8.0f);
        hour2.setSurplusEnergyKWh(2.0f);
        hour2.setGenerationEnergyKWh(null);
        hour2.setSelfConsumptionEnergyKWh(2.0f);

        DatadisConsumption hour3 = new DatadisConsumption();
        hour3.setCups("ES1234567890");
        hour3.setDate("2024/01");
        hour3.setTime("03:00");
        hour3.setObtainMethod("Real");
        hour3.setConsumptionKWh(null);
        hour3.setSurplusEnergyKWh(1.5f);
        hour3.setGenerationEnergyKWh(4.0f);
        hour3.setSelfConsumptionEnergyKWh(null);

        List<DatadisConsumption> hourlyConsumptions = List.of(hour1, hour2, hour3);

        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(hourlyConsumptions);

        ArgumentCaptor<List<DatadisConsumption>> monthlyCaptor = ArgumentCaptor.forClass(List.class);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(persistDatadisConsumptionRepository).persistMonthlyConsumptions(monthlyCaptor.capture());

        List<DatadisConsumption> monthlyAggregated = monthlyCaptor.getValue();
        assertEquals(1, monthlyAggregated.size());

        DatadisConsumption monthly = monthlyAggregated.get(0);
        assertEquals(18.0f, monthly.getConsumptionKWh(), 0.01f);
        assertEquals(3.5f, monthly.getSurplusEnergyKWh(), 0.01f);
        assertEquals(9.0f, monthly.getGenerationEnergyKWh(), 0.01f);
        assertEquals(5.0f, monthly.getSelfConsumptionEnergyKWh(), 0.01f);
    }

    @Test
    void testSynchronizeConsumptionsSkipsSupplyWithBlankDistributorCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributorCode("")
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributorCode("DIST123")
                .build();
        supply2 = createSupplyRepository.create(supply2, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 2, 28);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, never())
                .getHourlyConsumptionsByMonth(eq(supply1), any(Month.class), anyInt());
        verify(getDatadisConsumptionRepository, times(2))
                .getHourlyConsumptionsByMonth(eq(supply2), any(Month.class), anyInt());
    }

    @Test
    void testSynchronizeConsumptionsWithSingleDayRange() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("SINGLE")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 15);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.JANUARY), eq(2024));
        verify(persistDatadisConsumptionRepository, times(1)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(1)).persistYearlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsWithEmptyConsumptions() {
        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("SINGLE")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of();
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 15);
        LocalDate endDate = LocalDate.of(2024, 1, 15);
        service.synchronizeConsumptions(startDate, endDate);

        // Then
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply), eq(Month.JANUARY), eq(2024));
        verify(persistDatadisConsumptionRepository, times(0)).persistHourlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(0)).persistMonthlyConsumptions(anyList());
        verify(persistDatadisConsumptionRepository, times(0)).persistYearlyConsumptions(anyList());
    }
}
