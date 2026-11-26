package org.lucoenergia.conluz.infrastructure.production.plant.surplusclaim.file;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.PlantReference;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.PlantSurplusClaim;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDay;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimDayState;
import org.lucoenergia.conluz.domain.production.plant.surplusclaim.SurplusClaimHour;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Surplus claims built directly, as the preview's computation hands them to a file format: every local day of the
 * month in Madrid, with its 23, 24 or 25 hours. The formats read only the days, so the summary, the available months
 * and the warnings are left null.
 *
 * <p>By default every day is real and the hour {@code h} (0-based) of the day {@code d} holds {@code d + h / 100}.
 */
final class SurplusClaimFileTestClaims {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final String CIL = "ES0031300000000000000AB1F";
    static final UUID PLANT_ID = UUID.fromString("6f1c1a52-2f43-4c8e-9a43-3b2f6c0c3d11");

    private final YearMonth month;
    private String cil = CIL;
    private String regulatoryCode = "ES0031300000000000000AB1FA000";
    private final Map<LocalDate, SurplusClaimDayState> states = new HashMap<>();
    private final Map<LocalDate, Map<Integer, Double>> values = new HashMap<>();

    private SurplusClaimFileTestClaims(YearMonth month) {
        this.month = month;
    }

    static SurplusClaimFileTestClaims month(YearMonth month) {
        return new SurplusClaimFileTestClaims(month);
    }

    /**
     * The default value of an hour: the day of the month plus the hour's index in hundredths.
     */
    static double defaultValue(LocalDate date, int hour) {
        return date.getDayOfMonth() + hour / 100d;
    }

    SurplusClaimFileTestClaims cil(String cil) {
        this.cil = cil;
        return this;
    }

    SurplusClaimFileTestClaims regulatoryCode(String regulatoryCode) {
        this.regulatoryCode = regulatoryCode;
        return this;
    }

    SurplusClaimFileTestClaims estimated(LocalDate date) {
        states.put(date, SurplusClaimDayState.ESTIMATED);
        return this;
    }

    /**
     * Sets the value of one hour of a day, by its 0-based index; null for an hour without any record.
     */
    SurplusClaimFileTestClaims hour(LocalDate date, int hour, Double value) {
        values.computeIfAbsent(date, ignored -> new HashMap<>()).put(hour, value);
        return this;
    }

    PlantSurplusClaim build() {
        List<SurplusClaimDay> days = month.atDay(1).datesUntil(month.plusMonths(1).atDay(1))
                .map(this::day)
                .toList();
        return new PlantSurplusClaim(month, MADRID, new PlantReference(PLANT_ID, "North"), cil, regulatoryCode, null,
                null, days, null);
    }

    private SurplusClaimDay day(LocalDate date) {
        Instant end = date.plusDays(1).atStartOfDay(MADRID).toInstant();
        List<Instant> starts = Stream.iterate(date.atStartOfDay(MADRID).toInstant(), start -> start.isBefore(end),
                start -> start.plus(Duration.ofHours(1))).toList();
        Map<Integer, Double> overrides = values.getOrDefault(date, Map.of());
        List<SurplusClaimHour> hours = new ArrayList<>();
        for (int hour = 0; hour < starts.size(); hour++) {
            Double value = overrides.containsKey(hour) ? overrides.get(hour) : Double.valueOf(defaultValue(date, hour));
            hours.add(new SurplusClaimHour(starts.get(hour), value));
        }
        return new SurplusClaimDay(date, states.getOrDefault(date, SurplusClaimDayState.REAL), null, null,
                List.copyOf(hours));
    }
}
