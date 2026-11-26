package org.lucoenergia.conluz.infrastructure.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.ConsumptionCsvMonthInFutureException;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetSupplyDatadisConsumptionCsvService;
import org.lucoenergia.conluz.domain.consumption.datadis.get.SupplyDatadisConsumptionCsv;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.time.ZoneResolver;
import org.lucoenergia.conluz.infrastructure.datadis.csv.DatadisHourlyConsumptionCsv;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class GetSupplyDatadisConsumptionCsvServiceImpl implements GetSupplyDatadisConsumptionCsvService {

    private final GetDatadisConsumptionRepository getDatadisConsumptionRepository;
    private final GetSupplyRepository getSupplyRepository;
    private final ZoneResolver zoneResolver;
    private final Clock clock;

    public GetSupplyDatadisConsumptionCsvServiceImpl(
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
     * One zone bounds the month and labels every record's date and time, so a record written is always one whose
     * local date falls inside the month.
     */
    @Override
    public SupplyDatadisConsumptionCsv getHourlyCsv(SupplyId supplyId, YearMonth month) {
        ZoneId zone = zoneResolver.resolveZoneIdForSupply(supplyId.getId());
        YearMonth currentMonth = YearMonth.now(clock.withZone(zone));
        if (month.isAfter(currentMonth)) {
            throw new ConsumptionCsvMonthInFutureException(month, currentMonth);
        }
        Supply supply = getSupplyRepository.findById(supplyId)
                .orElseThrow(() -> new SupplyNotFoundException(supplyId));

        OffsetDateTime from = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime toExclusive = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        byte[] content = DatadisHourlyConsumptionCsv.write(
                getDatadisConsumptionRepository.getHourlyRecordsByPeriod(supply, from, toExclusive), zone);

        return new SupplyDatadisConsumptionCsv(supply.getCode() + "-" + month + ".csv", content);
    }
}
