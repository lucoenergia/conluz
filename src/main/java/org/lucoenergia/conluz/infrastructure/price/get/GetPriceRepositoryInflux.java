package org.lucoenergia.conluz.infrastructure.price.get;

import org.apache.commons.lang3.NotImplementedException;
import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.impl.InfluxDBResultMapper;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.get.GetPriceRepository;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourInfluxMapper;
import org.lucoenergia.conluz.infrastructure.price.PriceByHourPoint;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.lucoenergia.conluz.infrastructure.shared.db.influxdb.InfluxDbConnectionManager;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@Qualifier(value = "getPriceRepositoryInflux")
public class GetPriceRepositoryInflux implements GetPriceRepository {

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
    public List<PriceByHour> getPricesByDay(OffsetDateTime startDate) {
        throw new NotImplementedException();
    }
}
