package org.lucoenergia.conluz.price;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.shared.db.influxdb.InfluxDbConfiguration;
import org.lucoenergia.conluz.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.shared.db.influxdb.OffsetDateTimeToInfluxDbDateFormatConverter;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class GetPriceRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final PriceByHourMapper priceByHourMapper;

    private final InfluxDbConfiguration influxDbConfiguration;

    public GetPriceRepository(InfluxDbConnectionManager influxDbConnectionManager, PriceByHourMapper priceByHourMapper, InfluxDbConfiguration influxDbConfiguration) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.priceByHourMapper = priceByHourMapper;
        this.influxDbConfiguration = influxDbConfiguration;
    }

    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {

        InfluxDB connection = influxDbConnectionManager.getConnection();

        Query query = new Query(String.format(
                "SELECT * FROM \"omie-daily-prices\" WHERE time >= '%s' AND time <= '%s'",
                OffsetDateTimeToInfluxDbDateFormatConverter.convert(startDate),
                OffsetDateTimeToInfluxDbDateFormatConverter.convert(endDate)),
                influxDbConfiguration.getDatabaseName());

        QueryResult queryResult = connection.query(query);

        InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
        List<PriceByHourPoint> measurementPoints = resultMapper
                .toPOJO(queryResult, PriceByHourPoint.class);

        return priceByHourMapper.mapList(measurementPoints);
    }
}
