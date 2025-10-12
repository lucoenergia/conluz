package org.lucoenergia.conluz.infrastructure.shared.db.influxdb3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDb3Configuration {

    @Value("${spring.influxdb3.url}")
    private String databaseURL;

    @Value("${spring.influxdb3.bucket}")
    private String bucket;

    @Value("${spring.influxdb3.org}")
    private String organization;

    @Value("${spring.influxdb3.token}")
    private String token;

    public String getDatabaseURL() {
        return databaseURL;
    }

    public String getBucket() {
        return bucket;
    }

    public String getOrganization() {
        return organization;
    }

    public String getToken() {
        return token;
    }
}
