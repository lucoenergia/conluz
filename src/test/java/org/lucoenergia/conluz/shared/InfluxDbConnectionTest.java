package org.lucoenergia.conluz.shared;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Pong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class InfluxDbConnectionTest {

    @Value("${spring.influxdb.url}")
    private String databaseURL;

    @Test
    void testConnection() {
        InfluxDB influxDB = InfluxDBFactory.connect(databaseURL);

        Pong response = influxDB.ping();

        Assertions.assertTrue(response.isGood());

        influxDB.close();
    }
}
