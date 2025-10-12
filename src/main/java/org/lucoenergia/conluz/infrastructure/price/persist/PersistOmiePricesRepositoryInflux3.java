package org.lucoenergia.conluz.infrastructure.price.persist;

import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@Qualifier("persistOmiePricesRepositoryInflux3")
public class PersistOmiePricesRepositoryInflux3 implements PersistOmiePricesRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistOmiePricesRepositoryInflux3.class);

    private final InfluxDb3ConnectionManager connectionManager;

    public PersistOmiePricesRepositoryInflux3(InfluxDb3ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void persistPrices(List<PriceByHour> prices) {
        InfluxDBClient client = connectionManager.getClient();

        try {
            List<Point> points = new ArrayList<>();
            for (PriceByHour price : prices) {
                Instant timestamp = price.getHour().toInstant();

                Point point = Point.measurement(OmieConfig.PRICES_KWH_MEASUREMENT)
                        .setField(PriceByHourPoint.PRICE, price.getPrice())
                        .setTimestamp(timestamp);

                points.add(point);
            }
            if (!points.isEmpty()) {
                client.writePoints(points);
            }
        } catch (Exception e) {
            LOGGER.error("Unable to persist OMIE prices", e);
        }
    }
}
