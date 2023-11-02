package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

import org.influxdb.InfluxDB;

public interface InfluxDbConnectionManager {

    InfluxDB getConnection();
}
