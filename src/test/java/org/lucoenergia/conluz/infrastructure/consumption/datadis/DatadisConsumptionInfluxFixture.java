package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Writes and removes hourly consumption records for an arbitrary CUPS, for tests that need their
 * own dataset rather than the fixed one {@link DatadisConsumptionInfluxLoader} provides.
 *
 * <p>Records go through the production persistence repository, so a field left null on the
 * {@link DatadisConsumption} is absent from the stored point exactly as it would be in
 * production.</p>
 */
@Profile("test")
@Component
public class DatadisConsumptionInfluxFixture {

    private final PersistDatadisConsumptionRepository persistDatadisConsumptionRepository;
    private final InfluxDbConnectionManager influxDbConnectionManager;

    public DatadisConsumptionInfluxFixture(PersistDatadisConsumptionRepository persistDatadisConsumptionRepository,
                                           InfluxDbConnectionManager influxDbConnectionManager) {
        this.persistDatadisConsumptionRepository = persistDatadisConsumptionRepository;
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    /**
     * Builds an hourly record. A null energy value is stored as an absent field.
     *
     * @param date in {@code yyyy/MM/dd} format, interpreted in the application time zone
     * @param time in {@code HH:mm} format, interpreted in the application time zone
     */
    public static DatadisConsumption hourlyRecord(String cups, String date, String time, Float consumptionKWh,
                                                  Float selfConsumptionEnergyKWh, Float surplusEnergyKWh) {
        DatadisConsumption consumption = new DatadisConsumption();
        consumption.setCups(cups);
        consumption.setDate(date);
        consumption.setTime(time);
        consumption.setConsumptionKWh(consumptionKWh);
        consumption.setSelfConsumptionEnergyKWh(selfConsumptionEnergyKWh);
        consumption.setSurplusEnergyKWh(surplusEnergyKWh);
        consumption.setObtainMethod("Real");
        return consumption;
    }

    public void write(List<DatadisConsumption> consumptions) {
        persistDatadisConsumptionRepository.persistHourlyConsumptions(consumptions);
    }

    public void clear(String cups) {
        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {
            connection.query(new Query(String.format(
                    "DROP SERIES FROM \"%s\" WHERE \"cups\" = '%s'",
                    DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT, cups)));
        }
    }
}
