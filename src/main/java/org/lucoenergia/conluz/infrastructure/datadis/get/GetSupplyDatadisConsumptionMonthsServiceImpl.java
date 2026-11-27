package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetSupplyDatadisConsumptionMonthsService;
import org.lucoenergia.conluz.domain.consumption.datadis.get.SupplyDatadisConsumptionMonth;
import org.lucoenergia.conluz.domain.datadis.DatadisHourlyRecord;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class GetSupplyDatadisConsumptionMonthsServiceImpl implements GetSupplyDatadisConsumptionMonthsService {

    /**
     * The first whole day InfluxDB 1.x can represent, so no stored point is left out of the listing.
     */
    static final OffsetDateTime EARLIEST = OffsetDateTime.of(1677, 9, 22, 0, 0, 0, 0, ZoneOffset.UTC);

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetSupplyDatadisConsumptionMonthsServiceImpl(
            @Qualifier("getDatadisConsumptionRepositoryInflux") GetDatadisConsumptionRepository getDatadisConsumptionRepository,
            GetSupplyRepository getSupplyRepository,
            ZoneResolver zoneResolver,
            Clock clock) {
        this.getDatadisConsumptionRepository = getDatadisConsumptionRepository;
        this.getSupplyRepository = getSupplyRepository;
        this.zoneResolver = zoneResolver;
        this.clock = clock;
    }

    /**
     * The whole history is read in one query, with the read and the zone the CSV export uses, so each month counts
     * exactly the records its CSV writes. The query ends with the current month: the export refuses a later one,
     * and no record can exist there.
     */
    @Override
    public List<SupplyDatadisConsumptionMonth> getHourlyMonths(SupplyId supplyId) {
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        Supply supply = getSupplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(supplyId));

        OffsetDateTime toExclusive = YearMonth.now(clock.withZone(zone)).plusMonths(1).atDay(1).atStartOfDay(zone)
                .toOffsetDateTime();
        List<DatadisHourlyRecord<DatadisConsumption>> records =
                getDatadisConsumptionRepository.getHourlyRecordsByPeriod(supply, EARLIEST, toExclusive);

        Map<YearMonth, Long> hoursByMonth = new TreeMap<>(Comparator.reverseOrder());
        for (DatadisHourlyRecord<DatadisConsumption> record : records) {
            hoursByMonth.merge(YearMonth.from(record.instant().atZone(zone)), 1L, Long::sum);
        }
        return hoursByMonth.entrySet().stream()
                .map(month -> new SupplyDatadisConsumptionMonth(month.getKey(), month.getValue(),
                        EnergyMetricsPeriod.ofMonth(month.getKey(), zone).getExpectedHours()))
                .toList();
    }
}
