package org.lucoenergia.conluz.infrastructure.price;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.price.GetPriceRepository;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.DateToInfluxDbDateFormatConverter;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class GetPriceRepositoryInflux implements GetPriceRepository {

    private final InfluxDbConnectionManager influxDbConnectionManager;
    private final PriceByHourInfluxMapper priceByHourInfluxMapper;
    private final DateToInfluxDbDateFormatConverter dateConverter;

    public GetPriceRepositoryInflux(InfluxDbConnectionManager influxDbConnectionManager, PriceByHourInfluxMapper priceByHourInfluxMapper, DateToInfluxDbDateFormatConverter dateConverter) {
        this.influxDbConnectionManager = influxDbConnectionManager;
        this.priceByHourInfluxMapper = priceByHourInfluxMapper;
        this.dateConverter = dateConverter;
    }

    @Override
    public List<PriceByHour> getPricesByRangeOfDates(OffsetDateTime startDate, OffsetDateTime endDate) {

        try (InfluxDB connection = influxDbConnectionManager.getConnection()) {

            Query query = new Query(String.format(
                    "SELECT * FROM \"omie-daily-prices\" WHERE time >= '%s' AND time <= '%s'",
                    dateConverter.convert(startDate),
                    dateConverter.convert(endDate)));

            QueryResult queryResult = connection.query(query);

            InfluxDBResultMapper resultMapper = new InfluxDBResultMapper();
            List<PriceByHourPoint> measurementPoints = resultMapper
                    .toPOJO(queryResult, PriceByHourPoint.class);

            return priceByHourInfluxMapper.mapList(measurementPoints);
        }
    }
}
