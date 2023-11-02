package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.springframework.stereotype.Component;

@Component
public class InfluxDbConnectionManagerImpl implements InfluxDbConnectionManager {

    private final InfluxDbConfiguration config;

    public InfluxDbConnectionManagerImpl(InfluxDbConfiguration config) {
        this.config = config;
    }

    public InfluxDB getConnection() {
        return InfluxDBFactory.connect(config.getDatabaseURL(), config.getUsername(), config.getPassword());
    }
}
