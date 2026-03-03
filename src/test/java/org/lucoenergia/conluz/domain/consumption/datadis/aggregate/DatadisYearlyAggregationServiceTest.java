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
import org.lucoenergia.conluz.infrastructure.consumption.datadis.aggregate.DatadisYearlyAggregationServiceImpl;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@Transactional
class DatadisYearlyAggregationServiceTest extends BaseIntegrationTest {

    private final DatadisYearlyAggregationRepository aggregationRepository =
            Mockito.mock(DatadisYearlyAggregationRepository.class);

    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;

    private DatadisYearlyAggregationService service;

    @BeforeEach
    void setUp() {
        service = new DatadisYearlyAggregationServiceImpl(getSupplyRepository, aggregationRepository);
    }

    @Test
    void testAggregateYearlyForAllSupplies() {

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
        service.aggregateYearlyConsumptions(2024);

        // Then - Should call repository for each supply = 2 times
        verify(aggregationRepository, times(2))
                .aggregateYearlyConsumption(any(Supply.class), eq(2024));
    }

    @Test
    void testAggregateYearlyForSpecificSupply() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST123")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        // When
        service.aggregateYearlyConsumptions(SupplyCode.of(supply.getCode()), 2024);

        // Then
        verify(aggregationRepository, times(1))
                .aggregateYearlyConsumption(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlySkipsSuppliesWithoutDistributorCode() {

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
        service.aggregateYearlyConsumptions(2024);

        // Then - Should only call for supply with distributor code
        verify(aggregationRepository, times(1))
                .aggregateYearlyConsumption(eq(supplyWithCode), eq(2024));
        verify(aggregationRepository, never())
                .aggregateYearlyConsumption(eq(supplyWithoutCode), anyInt());
    }

    @Test
    void testAggregateYearlyWithSupplyNotFoundException() {

        // Given - no supplies created

        // When & Then
        assertThrows(SupplyNotFoundException.class, () ->
                service.aggregateYearlyConsumptions(SupplyCode.of("INVALID"), 2024)
        );

        verify(aggregationRepository, never())
                .aggregateYearlyConsumption(any(Supply.class), anyInt());
    }

    @Test
    void testAggregateYearlyHandlesRepositoryException() {

        // Given
        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        Supply supply = SupplyMother.random()
                .withDistributorCode("DIST123")
                .build();
        supply = createSupplyRepository.create(supply, UserId.of(user.getId()));

        doThrow(new RuntimeException("InfluxDB connection error"))
                .when(aggregationRepository)
                .aggregateYearlyConsumption(any(Supply.class), anyInt());

        // When - Should not throw exception, just log error
        service.aggregateYearlyConsumptions(2024);

        // Then - Should have attempted to aggregate
        verify(aggregationRepository, times(1))
                .aggregateYearlyConsumption(eq(supply), eq(2024));
    }

    @Test
    void testAggregateYearlyWithEmptySupplyList() {

        // Given - no supplies created

        // When
        service.aggregateYearlyConsumptions(2024);

        // Then - Should not call repository
        verify(aggregationRepository, never())
                .aggregateYearlyConsumption(any(Supply.class), anyInt());
    }
}
