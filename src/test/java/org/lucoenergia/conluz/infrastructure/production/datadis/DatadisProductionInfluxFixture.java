package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.lucoenergia.conluz.domain.production.datadis.PersistDatadisProductionRepository;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Writes and removes hourly production records for an arbitrary CUPS, for tests that need their own
 * dataset rather than the fixed one {@link DatadisProductionInfluxLoader} provides. Records go through
 * the production persistence repository.
 */
@Profile("test")
@Component
public class DatadisProductionInfluxFixture {

    private final PersistDatadisProductionRepository persistDatadisProductionRepository;
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public DatadisProductionInfluxFixture(PersistDatadisProductionRepository persistDatadisProductionRepository,
                                          InfluxDbConnectionManager influxDbConnectionManager) {
        this.persistDatadisProductionRepository = persistDatadisProductionRepository;
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    /**
     * Writes one hourly production record of the CUPS at an exact instant.
     */
    public void writeAt(String cups, Instant time, float productionKWh) {
        DatadisProduction production = new DatadisProduction();
        production.setCups(cups);
        production.setProductionKWh(productionKWh);
        production.setObtainMethod("Real");
        persistDatadisProductionRepository.persistHourlyProductions(
                List.of(new DatadisHourlyRecord<>(time, production)));
    }

    public void clear(String cups) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                    DatadisProductionMeasurements.PRODUCTION_KWH_MEASUREMENT, cups)));
        }
    }
}
