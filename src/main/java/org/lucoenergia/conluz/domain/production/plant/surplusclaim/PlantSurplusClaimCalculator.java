package org.lucoenergia.conluz.domain.production.plant.surplusclaim;

import org.lucoenergia.conluz.domain.admin.community.energy.SurplusIncome;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyPartitionCoefficient;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.SupplyReference;
import org.lucoenergia.conluz.domain.consumption.datadis.metrics.DailyPublishedAggregate;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings.SupplyWithGaps;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings.SupplyWithoutData;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimWarnings.UncoveredShare;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a plant's surplus claim for one month from figures already read, so every rule of the claim can be checked
 * without a store.
 *
 * <p>A coefficient is in force on a day when the day's local midnight lies inside its half-open period, as the
 * coefficients' own periods start and end at local midnight. Of the coefficients one supply holds in the plant during
 * the month, the one in force on the last day it held one is the one with the latest validFrom: the periods of one
 * supply in one plant never overlap.
 */
public final class PlantSurplusClaimCalculator {

    /**
     * Applied coefficients adding up to less than 1 minus this leave part of the production unshared; a shortfall
     * this small or smaller is taken as the rounding of the coefficients.
     */
    static final BigDecimal COVERAGE_TOLERANCE = new BigDecimal("0.0001");

    private PlantSurplusClaimCalculator() {
    }

    public static PlantSurplusClaim calculate(SurplusClaimInput input) {
        ZoneId zone = input.zone();
        YearMonth month = input.month();
        Instant monthStart = startOf(month.atDay(1), zone);
        Instant nextMonthStart = startOf(month.plusMonths(1).atDay(1), zone);
        List<SupplyPartitionCoefficient> coefficientsInMonth = input.appliedCoefficients().stream()
                .filter(coefficient -> overlaps(coefficient, monthStart, nextMonthStart))
                .toList();
        List<SupplyReference> scope = input.suppliesInScope().stream()
                .sorted(Comparator.comparing(SupplyReference::code))
                .toList();
        Map<String, Map<LocalDate, Long>> surplusHours = surplusHoursBySupplyAndDay(input.daysBySupplyCode());

        List<SurplusClaimDay> days = new ArrayList<>();
        Map<Instant, Double> claimedHours = new LinkedHashMap<>();
        List<SurplusClaimHour> hoursAboveNominalPower = new ArrayList<>();
        Map<String, List<LocalDate>> incompleteDaysBySupplyCode = new HashMap<>();
        List<LocalDate> uncoveredDays = new ArrayList<>();
        BigDecimal largestUncoveredShare = null;

        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            Instant dayStart = startOf(date, zone);
            Instant nextDayStart = startOf(date.plusDays(1), zone);

            List<SurplusClaimHour> hours = new ArrayList<>();
            for (Instant hour = dayStart; hour.isBefore(nextDayStart); hour = hour.plus(Duration.ofHours(1))) {
                SurplusClaimHour claimed = new SurplusClaimHour(hour, input.surplusByHour().get(hour));
                hours.add(claimed);
                if (claimed.surplusKWh() != null) {
                    claimedHours.put(hour, claimed.surplusKWh());
                    if (claimed.surplusKWh() > input.nominalPowerKW()) {
                        hoursAboveNominalPower.add(claimed);
                    }
                }
            }

            boolean everySupplyComplete = true;
            for (SupplyReference supply : scope) {
                long recorded = surplusHours.getOrDefault(supply.code(), Map.of()).getOrDefault(date, 0L);
                if (recorded < hours.size()) {
                    everySupplyComplete = false;
                    incompleteDaysBySupplyCode.computeIfAbsent(supply.code(), code -> new ArrayList<>()).add(date);
                }
            }
            SurplusClaimDayState state = !scope.isEmpty() && everySupplyComplete
                    ? SurplusClaimDayState.REAL
                    : SurplusClaimDayState.ESTIMATED;
            days.add(day(date, state, hours));

            if (!coefficientsInMonth.isEmpty()) {
                BigDecimal uncovered = BigDecimal.ONE.subtract(sumInForceAt(coefficientsInMonth, dayStart));
                if (uncovered.compareTo(COVERAGE_TOLERANCE) > 0) {
                    uncoveredDays.add(date);
                    largestUncoveredShare = largestUncoveredShare == null
                            ? uncovered
                            : largestUncoveredShare.max(uncovered);
                }
            }
        }

        List<SupplyWithoutData> suppliesWithoutData = new ArrayList<>();
        List<SupplyWithGaps> suppliesWithGaps = new ArrayList<>();
        int suppliesWithData = 0;
        for (SupplyReference supply : scope) {
            if (hasData(surplusHours.get(supply.code()))) {
                suppliesWithData++;
                List<LocalDate> incompleteDays = incompleteDaysBySupplyCode.get(supply.code());
                if (incompleteDays != null) {
                    suppliesWithGaps.add(new SupplyWithGaps(supply, List.copyOf(incompleteDays)));
                }
            } else {
                // A supply is in scope because it holds a coefficient in the month.
                suppliesWithoutData.add(new SupplyWithoutData(supply,
                        lastHeld(coefficientsInMonth, supply.id()).orElseThrow().getCoefficient()));
            }
        }

        Double surplusKWh = claimedHours.isEmpty()
                ? null
                : claimedHours.values().stream().mapToDouble(Double::doubleValue).sum();
        SurplusIncome surplusIncome = surplusKWh == null
                ? null
                : SurplusIncome.estimate(claimedHours, input.meanPriceByHour());
        int realDays = (int) days.stream().filter(day -> day.state() == SurplusClaimDayState.REAL).count();

        SurplusClaimSummary summary = new SurplusClaimSummary(surplusKWh, realDays, days.size() - realDays,
                scope.size(), suppliesWithData, surplusIncome);
        SurplusClaimWarnings warnings = new SurplusClaimWarnings(
                List.copyOf(suppliesWithoutData),
                List.copyOf(suppliesWithGaps),
                uncoveredDays.isEmpty() ? null : new UncoveredShare(largestUncoveredShare, List.copyOf(uncoveredDays)),
                lastHeld(coefficientsInMonth, input.plantSupplyId()).map(SupplyPartitionCoefficient::getCoefficient)
                        .orElse(null),
                coefficientsInMonth.isEmpty(),
                List.copyOf(hoursAboveNominalPower));

        return new PlantSurplusClaim(month, zone, input.plant(), input.cil(), input.availableMonths(), summary,
                List.copyOf(days), warnings);
    }

    /**
     * Whether the coefficient's half-open period shares any instant with {@code [from, toExclusive)}.
     */
    public static boolean overlaps(SupplyPartitionCoefficient coefficient, Instant from, Instant toExclusive) {
        return coefficient.getValidFrom() != null
                && coefficient.getValidFrom().isBefore(toExclusive)
                && (coefficient.getValidTo() == null || coefficient.getValidTo().isAfter(from));
    }

    private static SurplusClaimDay day(LocalDate date, SurplusClaimDayState state, List<SurplusClaimHour> hours) {
        Double total = null;
        SurplusClaimHour peak = null;
        for (SurplusClaimHour hour : hours) {
            if (hour.surplusKWh() == null) {
                continue;
            }
            total = total == null ? hour.surplusKWh() : total + hour.surplusKWh();
            // Strictly greater: on a tie the earlier hour stays the peak.
            if (peak == null || hour.surplusKWh() > peak.surplusKWh()) {
                peak = hour;
            }
        }
        if (total == null || total == 0d) {
            peak = null;
        }
        return new SurplusClaimDay(date, state, total, peak, List.copyOf(hours));
    }

    private static BigDecimal sumInForceAt(List<SupplyPartitionCoefficient> coefficients, Instant instant) {
        return coefficients.stream()
                .filter(coefficient -> !coefficient.getValidFrom().isAfter(instant)
                        && (coefficient.getValidTo() == null || coefficient.getValidTo().isAfter(instant)))
                .map(SupplyPartitionCoefficient::getCoefficient)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Optional<SupplyPartitionCoefficient> lastHeld(List<SupplyPartitionCoefficient> coefficientsInMonth,
                                                                 UUID supplyId) {
        return coefficientsInMonth.stream()
                .filter(coefficient -> coefficient.getSupplyId().equals(supplyId))
                .max(Comparator.comparing(SupplyPartitionCoefficient::getValidFrom));
    }

    private static Map<String, Map<LocalDate, Long>> surplusHoursBySupplyAndDay(
            Map<String, List<DailyPublishedAggregate>> daysBySupplyCode) {
        Map<String, Map<LocalDate, Long>> surplusHours = new HashMap<>();
        daysBySupplyCode.forEach((code, days) -> {
            Map<LocalDate, Long> byDay = new HashMap<>();
            days.forEach(day -> byDay.merge(day.day(), day.surplusHours(), Long::sum));
            surplusHours.put(code, byDay);
        });
        return surplusHours;
    }

    private static boolean hasData(Map<LocalDate, Long> surplusHoursByDay) {
        return surplusHoursByDay != null && surplusHoursByDay.values().stream().anyMatch(hours -> hours > 0);
    }

    private static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }
}
