package org.lucoenergia.conluz.infrastructure.consumption.datadis.persist;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistDatadisConsumptionRepositoryInflux implements PersistDatadisConsumptionRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final DateConverter dateConverter;

    public PersistDatadisConsumptionRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                                     DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void persistHourlyConsumptions(List<DatadisConsumption> consumptions) {
        persistHourlyConsumptions(consumptions, DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT);
    }

    @Override
    public void persistMonthlyConsumptions(List<DatadisConsumption> consumptions) {
        persistHourlyConsumptions(consumptions, DatadisConfigEntity.CONSUMPTION_KWH_MONTH_MEASUREMENT);
    }

    @Override
    public void persistYearlyConsumptions(List<DatadisConsumption> consumptions) {
        persistHourlyConsumptions(consumptions, DatadisConfigEntity.CONSUMPTION_KWH_YEAR_MEASUREMENT);
    }

    private void persistHourlyConsumptions(List<DatadisConsumption> consumptions, String measurementName) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (DatadisConsumption consumption : consumptions) {
                Point point = Point.measurement(measurementName)
                        .time(dateConverter.convertStringDateToMilliseconds(mergeDateAndTime(consumption)), TimeUnit.MILLISECONDS)
                        .tag("cups", consumption.getCups())
                        .addField("consumption_kwh", consumption.getConsumptionKWh())
                        .addField("obtain_method", consumption.getObtainMethod())
                        .addField("surplus_energy_kwh", consumption.getSurplusEnergyKWh())
                        .addField("generation_energy_kwh", consumption.getGenerationEnergyKWh())
                        .addField("self_consumption_energy_kwh", consumption.getSelfConsumptionEnergyKWh())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        }
    }

    private String mergeDateAndTime(DatadisConsumption consumption) {
        return String.format("%sT%s", consumption.getDate(), consumption.getTime());
    }
}
