package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Pong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class InfluxDbConnectionTest {

    @Autowired
    private InfluxDbConfiguration influxDbConfiguration;

    @Test
    void testConnection() {
        InfluxDB influxDB = InfluxDBFactory.connect(influxDbConfiguration.getDatabaseURL());

        Pong response = influxDB.ping();

        Assertions.assertTrue(response.isGood());

        influxDB.close();
    }
}
