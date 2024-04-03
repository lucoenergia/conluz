package org.lucoenergia.conluz.infrastructure.price.omie.persist;

import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourPoint;
import org.lucoenergia.conluz.infrastructure.price.omie.OmieConfig;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class PersistOmiePricesRepositoryInflux implements PersistOmiePricesRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistOmiePricesRepositoryInflux.class);

    private final InfluxDbConnectionManager influxDbConnectionManager;

    public PersistOmiePricesRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager) {
        this.influxDbConnectionManager = influxDbConnectionManager;
    }

    @Override
    public void persistPrices(List<PriceByHour> prices) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // Create new batch points object
            BatchPoints batchPoints = influxDbConnectionManager.createBatchPoints();

            for (PriceByHour price : prices) {
                Point point = Point.measurement(OmieConfig.PRICES_KWH_MEASUREMENT)
                        .time(price.getHour().toInstant().toEpochMilli(), TimeUnit.MILLISECONDS)
                        .addField(PriceByHourPoint.PRICE, price.getPrice())
                        .build();

                batchPoints.point(point);
            }
            connection.write(batchPoints);
        } catch (Exception e) {
            LOGGER.error("Unable to persist OMIE prices", e);
        }
    }
}
