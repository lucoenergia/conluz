package org.lucoenergia.conluz.domain.production.sharingagreement.observation;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.PublicationThreshold;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Decides which local days observed β may be computed on (OBS-001): both the consumer supply and the plant
 * supply reach the daily form of ENM-001's publication threshold, and the plant's production reaches
 * max(0.5 kWh × peak power, 12 kWh).
 */
final class ValidDayFilter {

    /**
     * The production a valid day needs per kWp of the plant's peak power: only very poor days are dropped.
     */
    static final double MIN_PRODUCTION_KWH_PER_KWP = 0.5;

    /**
     * The production a valid day needs whatever the plant's size. Datadis values carry 3 decimals, so the
     * rounding of a day's 24 values in E is at most 24 × 0.0005 = 0.012 kWh; at 12 kWh of production that is
     * 0.1 pp of β, the matching tolerance.
     */
    static final double MIN_PRODUCTION_KWH = 12.0;

    private final ZoneId zone;
    private final double minProductionKWh;

    ValidDayFilter(ZoneId zone, double peakPowerKWp) {
        this.zone = zone;
        this.minProductionKWh = Math.max(MIN_PRODUCTION_KWH_PER_KWP * peakPowerKWp, MIN_PRODUCTION_KWH);
    }

    double minProductionKWh() {
        return minProductionKWh;
    }

    /**
     * The plant's valid days inside {@code [from, to]}, with their production, in chronological order.
     */
    NavigableMap<LocalDate, Double> plantValidDays(List<PlantDailyEnergy> plantDays, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, Double> valid = new TreeMap<>();
        for (PlantDailyEnergy day : plantDays) {
            if (inside(day.day(), from, to) && isPublished(day.day(), day.publishedHours())
                    && day.productionKWh() >= minProductionKWh) {
                valid.put(day.day(), day.productionKWh());
            }
        }
        return Collections.unmodifiableNavigableMap(valid);
    }

    /**
     * The supply's valid days, in chronological order: those of its days that are plant-valid and published.
     */
    List<ValidDay> supplyValidDays(NavigableMap<LocalDate, Double> plantValidDays, List<SupplyDailyEnergy> days) {
        List<ValidDay> valid = new ArrayList<>();
        for (SupplyDailyEnergy day : days) {
            Double production = plantValidDays.get(day.day());
            if (production != null && isPublished(day.day(), day.publishedHours())) {
                valid.add(new ValidDay(day.day(), day.energyKWh(), production, day.energyKWh() / production));
            }
        }
        valid.sort((a, b) -> a.day().compareTo(b.day()));
        return valid;
    }

    private boolean isPublished(LocalDate day, long publishedHours) {
        // The daily form of ENM-001, as the daily series apply it: a day too short to hold a whole hour holds
        // no record.
        return publishedHours > 0 && PublicationThreshold.isReached(publishedHours,
                EnergyMetricsPeriod.ofDay(day, zone).getExpectedHours());
    }

    private static boolean inside(LocalDate day, LocalDate from, LocalDate to) {
        return !day.isBefore(from) && !day.isAfter(to);
    }
}
