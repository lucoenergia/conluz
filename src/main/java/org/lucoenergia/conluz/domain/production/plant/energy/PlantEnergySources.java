package org.lucoenergia.conluz.domain.production.plant.energy;

import org.lucoenergia.conluz.domain.consumption.RecordedConsumptionPeriod;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.DailyProductionAggregate;
import org.lucoenergia.conluz.domain.production.datadis.get.RecordedProductionPeriod;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A plant supply's energy and coverage per local day, from its two sources (OBS-001, ENM-009).
 *
 * <p>The Datadis sync stores a plant supply's production in {@code production_kwh}; before it did, the same
 * energy was stored as the supply's {@code surplus_energy_kwh} in the consumption measurement, and the sync now
 * stores that surplus as 0 whenever it writes production. The cutover day is the local day of the first
 * production record: every day before it is read from the legacy surplus, it and every day after it from
 * production. A day is never assembled from both, so the zeroed surplus is never read, and a cutover inside a
 * local day leaves that day with the production hours alone.</p>
 */
public final class PlantEnergySources {

    private PlantEnergySources() {
    }

    /**
     * The earlier of the supply's first production record and its first surplus record, or empty when it has
     * neither.
     */
    public static Optional<Instant> firstRecord(Optional<RecordedProductionPeriod> production,
                                                Optional<RecordedConsumptionPeriod> surplus) {
        return production.map(RecordedProductionPeriod::firstRecord)
                .map(first -> surplus.map(RecordedConsumptionPeriod::getFirstRecord)
                        .filter(legacy -> legacy.isBefore(first))
                        .orElse(first))
                .or(() -> surplus.map(RecordedConsumptionPeriod::getFirstRecord));
    }

    /**
     * The local day of the supply's first production record, or empty when it has none, in which case every day
     * is read from the legacy surplus.
     */
    public static Optional<LocalDate> cutover(Optional<RecordedProductionPeriod> production, ZoneId zone) {
        return production.map(period -> period.firstRecord().atZone(zone).toLocalDate());
    }

    /**
     * The supply's days, in chronological order, each from its own source: the legacy surplus before the
     * cutover, production from it on. A legacy day without any record carrying surplus is absent.
     *
     * @param production the supply's production per local day
     * @param legacy     the supply's consumption records per local day, in the same zone
     * @param cutover    the {@link #cutover} of the same supply
     */
    public static List<PlantDailyEnergy> daily(List<DailyProductionAggregate> production,
                                               List<DailyPublishedAggregate> legacy,
                                               Optional<LocalDate> cutover) {
        List<PlantDailyEnergy> days = new ArrayList<>();
        for (DailyPublishedAggregate day : legacy) {
            if (day.surplusHours() > 0 && cutover.map(day.day()::isBefore).orElse(true)) {
                days.add(new PlantDailyEnergy(day.day(), day.surplusKWh(), day.surplusHours(),
                        PlantEnergySource.LEGACY_SURPLUS));
            }
        }
        // No production day precedes the cutover: it is the day of the first production record.
        for (DailyProductionAggregate day : production) {
            days.add(new PlantDailyEnergy(day.day(), day.productionKWh(), day.hoursWithData(),
                    PlantEnergySource.PRODUCTION));
        }
        days.sort(Comparator.comparing(PlantDailyEnergy::day));
        return days;
    }

    /**
     * The days' hours, summed per calendar month.
     */
    public static Map<YearMonth, Long> hoursByMonth(List<PlantDailyEnergy> days) {
        Map<YearMonth, Long> hours = new TreeMap<>();
        days.forEach(day -> hours.merge(YearMonth.from(day.day()), day.hours(), Long::sum));
        return hours;
    }
}
