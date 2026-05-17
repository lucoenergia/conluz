package org.lucoenergia.conluz.domain.consumption.datadis.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.ConsumptionMother;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.DatadisSupplyConfigurationException;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.sync.DatadisConsumptionSyncServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void testSynchronizeConsumptionsForSingleMonth() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST123").build())
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
    }

    @Test
    void testSynchronizeConsumptionsForMultipleMonths() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST456").build())
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
    }

    @Test
    void testSynchronizeConsumptionsForFullYear() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST789").build())
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
    }

    @Test
    void testSynchronizeConsumptionsAcrossYears() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DISTXY").build())
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
    }

    @Test
    void testSynchronizeConsumptionsWithPartialData() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build())
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
    }

    @Test
    void testSynchronizeConsumptionsWithExceptionContinuesProcessing() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST003").build())
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
    }

    @Test
    void testSynchronizeConsumptionsMultipleSuppliesWithException() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("SUPPLY1").build())
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("SUPPLY2").build())
                .build();
        supply2 = createSupplyRepository.create(supply2, UserId.of(user.getId()));

        Supply supply3 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("SUPPLY3").build())
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
    }

    @Test
    void testSynchronizeConsumptionsAllSuppliesWithoutDistributorCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode(null).build())
                .build();
        createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode(null).build())
                .build();
        createSupplyRepository.create(supply2, UserId.of(user.getId()));

        Supply supply3 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode(null).build())
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
    }


    @Test
    void testSynchronizeConsumptionsSkipsSupplyWithBlankDistributorCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("").build())
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST123").build())
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
                .withDistributor(new SupplyDistributor.Builder().withCode("SINGLE").build())
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
    }

    @Test
    void testSynchronizeConsumptionsWithEmptyConsumptions() {
        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributor(new SupplyDistributor.Builder().withCode("SINGLE").build())
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
    }


    @Test
    void testSynchronizeConsumptionsForSingleSupplyWithValidCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withCode("SUPPLY001")
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST001").build())
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withCode("SUPPLY002")
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST002").build())
                .build();
        supply2 = createSupplyRepository.create(supply2, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        service.synchronizeConsumptions(startDate, endDate, SupplyCode.of("SUPPLY001"));

        // Then - only supply1 should be processed
        verify(getDatadisConsumptionRepository, times(1))
                .getHourlyConsumptionsByMonth(eq(supply1), eq(Month.JANUARY), eq(2024));
        verify(getDatadisConsumptionRepository, never())
                .getHourlyConsumptionsByMonth(eq(supply2), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, times(1)).persistHourlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsForSingleSupplyWithInvalidCode() {

        // Given - no supply created with code "INVALID"

        // When/Then
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        assertThrows(SupplyNotFoundException.class, () ->
                service.synchronizeConsumptions(startDate, endDate, SupplyCode.of("INVALID"))
        );

        verify(getDatadisConsumptionRepository, never())
                .getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, never()).persistHourlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsForSingleSupplyWithoutDistributorCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withCode("SUPPLY003")
                .withDistributor(new SupplyDistributor.Builder().withCode(null).build())
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        // When
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);
        service.synchronizeConsumptions(startDate, endDate, SupplyCode.of("SUPPLY003"));

        // Then - supply should be skipped
        verify(getDatadisConsumptionRepository, never())
                .getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt());
        verify(persistDatadisConsumptionRepository, never()).persistHourlyConsumptions(anyList());
    }

    @Test
    void testSynchronizeConsumptionsForSingleSupplyAcrossMultipleMonths() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withCode("SUPPLY004")
                .withDistributor(new SupplyDistributor.Builder().withCode("DIST004").build())
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        List<DatadisConsumption> consumptions = List.of(ConsumptionMother.random());
        when(getDatadisConsumptionRepository.getHourlyConsumptionsByMonth(any(Supply.class), any(Month.class), anyInt()))
                .thenReturn(consumptions);

        // When
        LocalDate startDate = LocalDate.of(2024, 3, 1);
        LocalDate endDate = LocalDate.of(2024, 5, 31);
        service.synchronizeConsumptions(startDate, endDate, SupplyCode.of("SUPPLY004"));

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
    }
}
