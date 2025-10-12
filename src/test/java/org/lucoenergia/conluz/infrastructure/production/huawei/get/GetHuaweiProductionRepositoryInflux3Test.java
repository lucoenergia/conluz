package org.lucoenergia.conluz.infrastructure.production.huawei.get;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.infrastructure.production.EnergyProductionInflux3Loader;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@SpringBootTest
class GetHuaweiProductionRepositoryInflux3Test extends BaseIntegrationTest {

    @Autowired
    @Qualifier("getHuaweiProductionRepositoryInflux3")
    private GetHuaweiProductionRepositoryInflux3 repository;

    @Autowired
    private EnergyProductionInflux3Loader energyProductionInflux3Loader;

    @BeforeEach
    void beforeEach() {
        energyProductionInflux3Loader.loadData();
    }

    @AfterEach
    void afterEach() {
        energyProductionInflux3Loader.clearData();
    }

    @Test
    void testGetRealTimeProductionByRangeOfDates() {
        // Arrange
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+00:00");

        // Act
        List<RealTimeProduction> result = repository.getRealTimeProductionByRangeOfDates(
                startDate.toInstant(),
                endDate.toInstant()
        );

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
    }

    @Test
    void testGetRealTimeProductionVerifyData() {
        // Arrange
        OffsetDateTime startDate = OffsetDateTime.parse("2023-09-01T00:00:00.000+00:00");
        OffsetDateTime endDate = OffsetDateTime.parse("2023-09-01T23:00:00.000+00:00");

        // Act
        List<RealTimeProduction> result = repository.getRealTimeProductionByRangeOfDates(
                startDate.toInstant(),
                endDate.toInstant()
        );

        // Assert
        Assertions.assertFalse(result.isEmpty());
        RealTimeProduction firstProduction = result.get(0);
        Assertions.assertNotNull(firstProduction.getTime());
        Assertions.assertNotNull(firstProduction.getStationCode());
    }

    @Test
    void testGetRealTimeProductionEmptyResult() {
        // Arrange - Query a date range with no data
        Instant startDate = OffsetDateTime.parse("2024-01-01T00:00:00.000+00:00").toInstant();
        Instant endDate = OffsetDateTime.parse("2024-01-01T23:00:00.000+00:00").toInstant();

        // Act
        List<RealTimeProduction> result = repository.getRealTimeProductionByRangeOfDates(startDate, endDate);

        // Assert
        Assertions.assertTrue(result.isEmpty());
    }
}
