package org.lucoenergia.conluz.infrastructure.consumption.datadis;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.consumption.datadis.Consumption;
import org.lucoenergia.conluz.domain.consumption.datadis.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.shared.datadis.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateToMillisecondsConverter;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistDatadisConsumptionRepositoryInflux implements PersistDatadisConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateToMillisecondsConverter dateToMillisecondsConverter;

    public PersistDatadisConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                     DateToMillisecondsConverter dateToMillisecondsConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateToMillisecondsConverter = dateToMillisecondsConverter;
    }

    @Override
    public void persistConsumptions(List<Consumption> consumptions) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            for (Consumption consumption : consumptions) {
                connection.write(Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                        .time(dateToMillisecondsConverter.convert(mergeDateAndTime(consumption)), TimeUnit.MILLISECONDS)
                        .tag("cups", consumption.getCups())
                        .addField("consumption_kwh", consumption.getConsumptionKWh())
                        .addField("obtain_method", consumption.getObtainMethod())
                        .addField("surplus_energy_kwh", consumption.getSurplusEnergyKWh())
                        .build());
            }
        }
    }

    private String mergeDateAndTime(Consumption consumption) {
        return String.format("%sT%s", consumption.getDate(), consumption.getTime());
    }
}
