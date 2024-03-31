package org.lucoenergia.conluz.infrastructure.shared.db.influxdb;

public interface InfluxLoader {

    void loadData();

    void clearData();
}
