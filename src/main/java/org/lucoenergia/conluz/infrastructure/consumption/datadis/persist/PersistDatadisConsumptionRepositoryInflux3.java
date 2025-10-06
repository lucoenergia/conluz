package org.lucoenergia.conluz.infrastructure.consumption.datadis.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.persist.PersistDatadisConsumptionRepository;
import org.lucoenergia.conluz.infrastructure.consumption.datadis.config.DatadisConfigEntity;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Qualifier("persistDatadisConsumptionRepositoryInflux3")
public class PersistDatadisConsumptionRepositoryInflux3 implements PersistDatadisConsumptionRepository {

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;

    public PersistDatadisConsumptionRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                                      DateConverter dateConverter) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public void persistConsumptions(List<DatadisConsumption> consumptions) {
        InfluxDBClient client = connectionManager.getClient();

        try {
            for (DatadisConsumption consumption : consumptions) {
                long timestampMillis = dateConverter.convertStringDateToMilliseconds(mergeDateAndTime(consumption));
                Instant timestamp = Instant.ofEpochMilli(timestampMillis);

                Point point = Point.measurement(DatadisConfigEntity.CONSUMPTION_KWH_MEASUREMENT)
                        .setTag("cups", consumption.getCups())
                        .setField("consumption_kwh", consumption.getConsumptionKWh())
                        .setField("obtain_method", consumption.getObtainMethod())
                        .setField("surplus_energy_kwh", consumption.getSurplusEnergyKWh())
                        .setField("generation_energy_kwh", consumption.getGenerationEnergyKWh())
                        .setField("self_consumption_energy_kwh", consumption.getSelfConsumptionEnergyKWh())
                        .setTimestamp(timestamp);

                client.writePoint(point);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error persisting Datadis consumptions", e);
        }
    }

    private String mergeDateAndTime(DatadisConsumption consumption) {
        return String.format("%sT%s", consumption.getDate(), consumption.getTime());
    }
}
