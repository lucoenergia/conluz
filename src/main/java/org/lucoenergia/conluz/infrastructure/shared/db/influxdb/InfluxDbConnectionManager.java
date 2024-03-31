package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;

public interface InfluxDbConnectionManager {

    InfluxDB getConnection();

    BatchPoints createBatchPoints();

    BatchPoints createBatchPoints(RetentionPolicy policy);
}
