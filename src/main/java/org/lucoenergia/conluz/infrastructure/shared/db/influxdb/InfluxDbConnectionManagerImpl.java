package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.apache.commons.lang3.StringUtils;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.springframework.stereotype.Component;

@Component
public class InfluxDbConnectionManagerImpl implements InfluxDbConnectionManager {

    private final InfluxDbConfiguration config;

    public InfluxDbConnectionManagerImpl(InfluxDbConfiguration config) {
        this.config = config;
    }

    public InfluxDB getConnection() {
        String username = config.getUsername();
        String password = config.getPassword();
        InfluxDB connection;
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            connection = InfluxDBFactory.connect(config.getDatabaseURL());
        } else {
            connection = InfluxDBFactory.connect(config.getDatabaseURL(), username, password);
        }
        connection.setDatabase(config.getDatabaseName());
        connection.setLogLevel(InfluxDB.LogLevel.NONE);
        connection.enableGzip();
        return connection;
    }

    @Override
    public BatchPoints createBatchPoints() {
        return BatchPoints
                .database(config.getDatabaseName())
                .build();
    }

    @Override
    public BatchPoints createBatchPoints(RetentionPolicy policy) {
        return BatchPoints
                .database(config.getDatabaseName())
                .retentionPolicy(policy.name().toLowerCase())
                .build();
    }
}
