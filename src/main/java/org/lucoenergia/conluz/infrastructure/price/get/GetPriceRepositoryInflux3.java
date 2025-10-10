package org.lucoenergia.conluz.infrastructure.price.get;

import com.influxdb.v3.client.InfluxDBClient;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb3.InfluxDb3ConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Repository
@Qualifier(value = "getPriceRepositoryInflux3")
public class GetPriceRepositoryInflux3 implements GetPriceRepository {

    private final InfluxDb3ConnectionManager connectionManager;
    private final DateConverter dateConverter;

    public GetPriceRepositoryInflux3(InfluxDb3ConnectionManager connectionManager,
                                     DateConverter dateConverter) {
        this.connectionManager = connectionManager;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {
        InfluxDBClient client = connectionManager.getClient();

        String query = String.format(
                "SELECT time, price1 FROM \"%s\" WHERE time >= '%s' AND time <= '%s' ORDER BY time",
                OmieConfig.PRICES_KWH_MEASUREMENT,
                dateConverter.convertToString(startDate),
                dateConverter.convertToString(endDate)
        );

        return executeQuery(client, query);
    }

    private List<PriceByHour> executeQuery(InfluxDBClient client, String query) {
        List<PriceByHour> results = new ArrayList<>();

        try (Stream<Object[]> stream = client.query(query)) {
            stream.forEach(row -> {
                // Expected columns: time, price1
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    // InfluxDB 3 returns timestamps as Instant or sometimes as BigInteger (nanoseconds)
                    Instant time;
                    if (row[0] instanceof Instant) {
                        time = (Instant) row[0];
                    } else if (row[0] instanceof Number) {
                        long nanos = ((Number) row[0]).longValue();
                        time = Instant.ofEpochSecond(0, nanos);
                    } else {
                        throw new IllegalArgumentException("Unexpected timestamp type: " + row[0].getClass());
                    }

                    Double price = ((Number) row[1]).doubleValue();

                    PriceByHourPoint point = new PriceByHourPoint(time, price);
                    results.add(mapToPriceByHour(point));
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Error querying price data", e);
        }

        return results;
    }

    private PriceByHour mapToPriceByHour(PriceByHourPoint point) {
        OffsetDateTime hour = dateConverter.convertInstantToOffsetDateTime(point.getTime());
        return new PriceByHour(point.getPrice(), hour);
    }
}
