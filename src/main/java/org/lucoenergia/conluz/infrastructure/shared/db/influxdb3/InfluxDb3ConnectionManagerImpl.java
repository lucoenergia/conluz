package org.lucoenergia.conluz.infrastructure.shared.db.influxdb3;

import com.influxdb.v3.client.InfluxDBClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InfluxDb3ConnectionManagerImpl implements InfluxDb3ConnectionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDb3ConnectionManagerImpl.class);

    private final InfluxDb3Configuration config;
    private InfluxDBClient client;

    public InfluxDb3ConnectionManagerImpl(InfluxDb3Configuration config) {
        this.config = config;
    }

    @Override
    public InfluxDBClient getClient() {
        if (client == null) {
            String token = config.getToken();
            if (StringUtils.isBlank(token)) {
                client = InfluxDBClient.getInstance(
                        config.getDatabaseURL(),
                        null,
                        config.getBucket()
                );
            } else {
                client = InfluxDBClient.getInstance(
                        config.getDatabaseURL(),
                        token.toCharArray(),
                        config.getBucket()
                );
            }
        }
        return client;
    }

    @Override
    public void close() {
        if (client != null) {
            try {
                client.close();
                client = null;
            } catch (Exception e) {
                LOGGER.error("Error closing InfluxDB client", e);
            }
        }
    }
}
