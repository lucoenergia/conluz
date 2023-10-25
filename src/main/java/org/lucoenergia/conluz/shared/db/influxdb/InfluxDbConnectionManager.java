package org.lucoenergia.conluz.shared.db.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InfluxDbConnectionManager {

    private final InfluxDbConfiguration influxDbConfiguration;

    public InfluxDbConnectionManager(InfluxDbConfiguration influxDbConfiguration) {
        this.influxDbConfiguration = influxDbConfiguration;
    }

    public InfluxDB getConnection() {
        return InfluxDBFactory.connect(influxDbConfiguration.getDatabaseURL());
    }

    public void closeConnection(InfluxDB connection) {
        if (connection != null) {
            connection.close();
        }
    }
}
