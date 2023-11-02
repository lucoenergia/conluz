package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDbConfiguration {

    @Value("${spring.influxdb.url}")
    private String databaseURL;

    @Value("${spring.influxdb.database}")
    private String databaseName;

    @Value("${spring.influxdb.username}")
    private String username;
    @Value("${spring.influxdb.password}")
    private String password;

    public String getDatabaseURL() {
        return databaseURL;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
