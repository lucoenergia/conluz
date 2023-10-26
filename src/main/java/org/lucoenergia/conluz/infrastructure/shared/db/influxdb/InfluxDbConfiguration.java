package org.lucoenergia.conluz.shared.db.influxdb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDbConfiguration {

    @Value("${spring.influxdb.url}")
    private String databaseURL;

    @Value("${spring.influxdb.database}")
    private String databaseName;

    public String getDatabaseURL() {
        return databaseURL;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
