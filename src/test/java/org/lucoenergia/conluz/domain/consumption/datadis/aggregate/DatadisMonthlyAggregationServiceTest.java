package org.lucoenergia.conluz.domain.consumption.datadis.aggregate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate.DatadisMonthlyAggregationServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@Transactional
class DatadisMonthlyAggregationServiceTest extends BaseIntegrationTest {

    private final DatadisMonthlyAggregationRepository aggregationRepository =
            Mockito.mock(DatadisMonthlyAggregationRepository.class);

    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;

    private DatadisMonthlyAggregationService service;

    @BeforeEach
    void setUp() {
        service = new DatadisMonthlyAggregationServiceImpl(getSupplyRepository, aggregationRepository);
    }

    @Test
    void testAggregateMonthlyForAllSuppliesAllMonths() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributorCode("DIST001")
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributorCode("DIST002")
                .build();
        supply2 = createSupplyRepository.create(supply2, UserId.of(user.getId()));

        // When
        service.aggregateMonthlyConsumptions(2024);

        // Then - Should call repository for each supply × 12 months = 24 times
        verify(aggregationRepository, times(24))
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), eq(2024));
    }

    @Test
    void testAggregateMonthlyForSpecificSupplyAndMonth() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST123")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // When
        service.aggregateMonthlyConsumptions(SupplyCode.of(supply.getCode()), Month.JUNE, 2024);

        // Then
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supply), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyForAllSuppliesSpecificMonth() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply1 = SupplyMother.random()
                .withDistributorCode("DIST001")
                .build();
        supply1 = createSupplyRepository.create(supply1, UserId.of(user.getId()));

        Supply supply2 = SupplyMother.random()
                .withDistributorCode("DIST002")
                .build();
        supply2 = createSupplyRepository.create(supply2, UserId.of(user.getId()));

        // When
        service.aggregateMonthlyConsumptions(Month.DECEMBER, 2024);

        // Then - Should call repository for each supply × 1 month = 2 times
        verify(aggregationRepository, times(2))
                .aggregateMonthlyConsumption(any(Supply.class), eq(Month.DECEMBER), eq(2024));
    }

    @Test
    void testAggregateMonthlySkipsSuppliesWithoutDistributorCode() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supplyWithCode = SupplyMother.random()
                .withDistributorCode("DIST001")
                .build();
        supplyWithCode = createSupplyRepository.create(supplyWithCode, UserId.of(user.getId()));

        Supply supplyWithoutCode = SupplyMother.random()
                .withDistributorCode(null)
                .build();
        supplyWithoutCode = createSupplyRepository.create(supplyWithoutCode, UserId.of(user.getId()));

        // When
        service.aggregateMonthlyConsumptions(Month.JANUARY, 2024);

        // Then - Should only call for supply with distributor code
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supplyWithCode), eq(Month.JANUARY), eq(2024));
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(eq(supplyWithoutCode), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyWithSupplyNotFoundException() {

        // Given - no supplies created

        // When & Then
        assertThrows(SupplyNotFoundException.class, () ->
                service.aggregateMonthlyConsumptions(SupplyCode.of("INVALID"), Month.JUNE, 2024)
        );

        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }

    @Test
    void testAggregateMonthlyHandlesRepositoryException() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST123")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());

        // When - Should not throw exception, just log error
        service.aggregateMonthlyConsumptions(Month.JUNE, 2024);

        // Then - Should have attempted to aggregate
        verify(aggregationRepository, times(1))
                .aggregateMonthlyConsumption(eq(supply), eq(Month.JUNE), eq(2024));
    }

    @Test
    void testAggregateMonthlyWithEmptySupplyList() {

        // Given - no supplies created

        // When
        service.aggregateMonthlyConsumptions(2024);

        // Then - Should not call repository
        verify(aggregationRepository, never())
                .aggregateMonthlyConsumption(any(Supply.class), any(Month.class), anyInt());
    }
}
