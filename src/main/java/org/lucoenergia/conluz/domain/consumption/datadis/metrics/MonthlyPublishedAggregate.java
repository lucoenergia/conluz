package org.lucoenergia.conluz.domain.consumption.datadis.metrics;

import org.lucoenergia.conluz.domain.consumption.EnergyMetricsPeriod;
import org.lucoenergia.conluz.domain.consumption.PublicationThreshold;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Totals of a supply's hourly consumption records over one local calendar month, folded from its
 * {@link DailyPublishedAggregate days}, unrounded.
 *
 * @param month              the local calendar month
 * @param consumptionKWh     the days' grid import summed
 * @param selfConsumptionKWh the days' self-consumption summed
 * @param surplusKWh         the days' surplus summed
 * @param publishedHours     the number of records carrying self-consumption, zero included
 * @param obtainMethod       the obtain method of the month's last day that carries one, or null when none does
 */
public record MonthlyPublishedAggregate(YearMonth month, double consumptionKWh, double selfConsumptionKWh,
                                        double surplusKWh, long publishedHours, String obtainMethod) {

    /**
     * One supply's days added up per local calendar month, in the order the days come in; the
     * obtain method kept is that of the last day carrying one, so chronological days give the month's
     * last obtain method.
     */
    public static Map<YearMonth, MonthlyPublishedAggregate> foldIntoMonths(List<DailyPublishedAggregate> days) {
        Map<YearMonth, MonthlyPublishedAggregate> months = new LinkedHashMap<>();
        for (DailyPublishedAggregate day : days) {
            YearMonth month = YearMonth.from(day.day());
            MonthlyPublishedAggregate sofar = months.getOrDefault(month,
                    new MonthlyPublishedAggregate(month, 0d, 0d, 0d, 0L, null));
            months.put(month, new MonthlyPublishedAggregate(month,
                    sofar.consumptionKWh + day.consumptionKWh(),
                    sofar.selfConsumptionKWh + day.selfConsumptionKWh(),
                    sofar.surplusKWh + day.surplusKWh(),
                    sofar.publishedHours + day.publishedHours(),
                    day.obtainMethod() != null ? day.obtainMethod() : sofar.obtainMethod));
        }
        return months;
    }

    /**
     * Whether the supply has data in this month: its records carrying self-consumption reach the
     * {@link PublicationThreshold publication threshold} of the month's hours inside {@code period}.
     */
    public boolean isPublishedWithin(EnergyMetricsPeriod period, ZoneId zone) {
        long possibleHours = period.hoursOverlapping(EnergyMetricsPeriod.ofMonth(month, zone));
        return publishedHours > 0 && PublicationThreshold.isReached(publishedHours, possibleHours);
    }
}
