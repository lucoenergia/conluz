package org.lucoenergia.conluz.infrastructure.production.huawei.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.production.huawei.HourlyProduction;
import org.lucoenergia.conluz.domain.production.huawei.RealTimeProduction;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistHuaweiProductionRepositoryInflux3Test {

    @InjectMocks
    private PersistHuaweiProductionRepositoryInflux3 repository;

    @Mock
    private InfluxDb3ConnectionManager connectionManager;

    @Mock
    private InfluxDBClient client;

    @Test
    void testPersistRealTimeProductionSingle() {
        // Arrange
        RealTimeProduction production = new RealTimeProduction.Builder()
                .setTime(Instant.now())
                .setStationCode("SC123")
                .setRealHealthState(1)
                .setDayPower(2.5)
                .setTotalPower(100.0)
                .setDayIncome(5.0)
                .setMonthPower(75.0)
                .setTotalIncome(500.0)
                .build();

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistRealTimeProduction(Collections.singletonList(production));

        // Assert
        verify(client, times(1)).writePoints(any(List.class));
    }

    @Test
    void testPersistRealTimeProductionMultiple() {
        // Arrange
        RealTimeProduction production1 = new RealTimeProduction.Builder()
                .setTime(Instant.now())
                .setStationCode("SC123")
                .setRealHealthState(1)
                .setDayPower(2.5)
                .setTotalPower(100.0)
                .setDayIncome(5.0)
                .setMonthPower(75.0)
                .setTotalIncome(500.0)
                .build();

        RealTimeProduction production2 = new RealTimeProduction.Builder()
                .setTime(Instant.now().plusSeconds(3600))
                .setStationCode("SC124")
                .setRealHealthState(1)
                .setDayPower(3.0)
                .setTotalPower(110.0)
                .setDayIncome(6.0)
                .setMonthPower(80.0)
                .setTotalIncome(550.0)
                .build();

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistRealTimeProduction(Arrays.asList(production1, production2));

        // Assert
        verify(client, times(1)).writePoints(any(List.class));
    }

    @Test
    void testPersistHourlyProductionSingle() {
        // Arrange
        HourlyProduction production = new HourlyProduction.Builder()
                .withTime(Instant.now())
                .withStationCode("SC123")
                .withInverterPower(25.5)
                .withOngridPower(24.0)
                .withPowerProfit(5.0)
                .withTheoryPower(30.0)
                .withRadiationIntensity(800.0)
                .build();

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistHourlyProduction(Collections.singletonList(production));

        // Assert
        verify(client, times(1)).writePoints(any(List.class));
    }

    @Test
    void testPersistHourlyProductionMultiple() {
        // Arrange
        HourlyProduction production1 = new HourlyProduction.Builder()
                .withTime(Instant.now())
                .withStationCode("SC123")
                .withInverterPower(25.5)
                .withOngridPower(24.0)
                .withPowerProfit(5.0)
                .withTheoryPower(30.0)
                .withRadiationIntensity(800.0)
                .build();

        HourlyProduction production2 = new HourlyProduction.Builder()
                .withTime(Instant.now().plusSeconds(3600))
                .withStationCode("SC123")
                .withInverterPower(28.0)
                .withOngridPower(27.0)
                .withPowerProfit(5.5)
                .withTheoryPower(32.0)
                .withRadiationIntensity(850.0)
                .build();

        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistHourlyProduction(Arrays.asList(production1, production2));

        // Assert
        verify(client, times(1)).writePoints(any(List.class));
    }

    @Test
    void testPersistRealTimeProductionEmptyList() {
        // Arrange
        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistRealTimeProduction(Collections.emptyList());

        // Assert
        verify(client, times(0)).writePoints(any(List.class));
    }

    @Test
    void testPersistHourlyProductionEmptyList() {
        // Arrange
        when(connectionManager.getClient()).thenReturn(client);

        // Act
        repository.persistHourlyProduction(Collections.emptyList());

        // Assert
        verify(client, times(0)).writePoints(any(List.class));
    }
}
