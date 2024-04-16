package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.impl.InfluxDBImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

// InfluxDbConnectionManagerImplTest unit test class for InfluxDbConnectionManagerImpl
class InfluxDbConnectionManagerImplTest {

    // Test to ensure calling getConnection method when username and password are null or empty
    @Test
    void getConnectionWithEmptyUsernameAndPasswordTest() {
        // Arrange
        InfluxDbConfiguration config = mock(InfluxDbConfiguration.class);
        when(config.getDatabaseURL()).thenReturn("http://localhost:8086");
        when(config.getUsername()).thenReturn("");
        when(config.getPassword()).thenReturn("");
        when(config.getDatabaseName()).thenReturn("testdb"); //For line4

        // Act
        InfluxDbConnectionManagerImpl manager = new InfluxDbConnectionManagerImpl(config);
        InfluxDB connection = manager.getConnection();

        // Assert
        verify(config, times(1)).getDatabaseURL();
        verify(config, times(1)).getUsername();
        verify(config, times(1)).getPassword();
        verify(config, times(1)).getDatabaseName();
        Assertions.assertNotNull(connection);
        Assertions.assertTrue(connection.isGzipEnabled());
    }

    // Test to ensure calling getConnection method when username and password are not null
    @Test
    void getConnectionWithValidUsernameAndPasswordTest() {
        // Arrange
        InfluxDbConfiguration config = mock(InfluxDbConfiguration.class);
        when(config.getDatabaseURL()).thenReturn("http://localhost:8086");
        when(config.getUsername()).thenReturn("testuser");
        when(config.getPassword()).thenReturn("testpass");
        when(config.getDatabaseName()).thenReturn("testdb");

        // Act
        InfluxDbConnectionManagerImpl manager = new InfluxDbConnectionManagerImpl(config);
        InfluxDB connection = manager.getConnection();

        // Assert
        verify(config, times(1)).getDatabaseURL();
        verify(config, times(1)).getUsername();
        verify(config, times(1)).getPassword();
        verify(config, times(1)).getDatabaseName();
        Assertions.assertNotNull(connection);
        Assertions.assertTrue(connection.isGzipEnabled());
    }
}