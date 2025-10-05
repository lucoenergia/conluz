package org.lucoenergia.conluz.infrastructure.shared.db.influxdb3;

import com.influxdb.v3.client.InfluxDBClient;

public interface InfluxDb3ConnectionManager {

    InfluxDBClient getClient();

    void close();
}
