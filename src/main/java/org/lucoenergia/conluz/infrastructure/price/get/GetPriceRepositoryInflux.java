package org.lucoenergia.conluz.infrastructure.price.get;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourInfluxMapper;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourPoint;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
@Qualifier(value = "getPriceRepositoryInflux")
public class GetPriceRepositoryInflux implements GetPriceRepository {

    private static final String COLUMN_TIME = "time";

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final PriceByHourInfluxMapper priceByHourInfluxMapper;
    private final DateConverter dateConverter;

    public GetPriceRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager,
                                    PriceByHourInfluxMapper priceByHourInfluxMapper, DateConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.priceByHourInfluxMapper = priceByHourInfluxMapper;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM %s WHERE time >= '%s' AND time <= '%s'",
                    OmieConfig.PRICES_KWH_MEASUREMENT,
                    dateConverter.convertToString(startDate),
                    dateConverter.convertToString(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<PriceByHourPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, PriceByHourPoint.class);

            return priceByHourInfluxMapper.mapList(measurementPoints);
        }
    }

    @Override
    public Map<Instant, Double> meanPriceByHour(Instant from, Instant toExclusive, ZoneId zone) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            // The store averages each hour's points, four quarter-hour prices or a single hourly one.
            // fill(none) leaves out the hours without any price, and tz() aligns the hours as the
            // community's other queries do.
            Query query = new Query(String.format(
                    """
                            SELECT MEAN("%s") AS "%s"
                            FROM "%s"
                            WHERE time >= '%s'
                                AND time < '%s'
                            GROUP BY time(1h) fill(none) tz('%s')
                            """,
                    PriceByHourPoint.PRICE,
                    PriceByHourPoint.PRICE,
                    OmieConfig.PRICES_KWH_MEASUREMENT,
                    dateConverter.convertToString(from),
                    dateConverter.convertToString(toExclusive),
                    zone.getId()));

            // Times in milliseconds come back as a number, whatever the server's precision.
            QueryResult queryResult = connection.query(query, TimeUnit.MILLISECONDS);
            Map<Instant, Double> priceByHour = new HashMap<>();
            if (queryResult == null || queryResult.getResults() == null) {
                return priceByHour;
            }
            queryResult.getResults().stream()
                    .filter(result -> result.getSeries() != null)
                    .flatMap(result -> result.getSeries().stream())
                    .filter(series -> series.getValues() != null)
                    .forEach(series -> {
                        int timeIndex = series.getColumns().indexOf(COLUMN_TIME);
                        int priceIndex = series.getColumns().indexOf(PriceByHourPoint.PRICE);
                        for (List<Object> row : series.getValues()) {
                            if (priceIndex >= 0 && priceIndex < row.size()
                                    && row.get(priceIndex) instanceof Number price) {
                                priceByHour.put(dateConverter.convertMillisecondsToInstant(
                                        ((Number) row.get(timeIndex)).longValue()), price.doubleValue());
                            }
                        }
                    });
            return priceByHour;
        }
    }
}
