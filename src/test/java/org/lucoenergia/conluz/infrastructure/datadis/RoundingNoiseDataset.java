package org.lucoenergia.conluz.infrastructure.datadis;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.consumption.datadis.get.GetDatadisConsumptionRepository;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.datadis.DatadisConsumptionInfluxFixture.hourlyRecord;

/**
 * Two hourly records whose figures carry four decimals, for the tests of how energy figures are
 * rounded in responses (#385).
 *
 * <p>Summed, every figure leaves more than 3 decimals behind -- 0.1234 + 0.2222 kWh of grid import
 * reads 0.3456 on the float series and 0.3456000089645386 on the double metrics -- so a response that
 * does not round fails the precision assertion. Rounded record by record before summing, grid import
 * reads 0.345 and generation 0.112 instead of the 0.346 and 0.111 the unrounded sums round to, so a
 * response that rounds too early fails the value assertion. Self-consumed energy is worth
 * 0.0334 kWh x 0.15 EUR = 0.00501 EUR, presented as 0.01; priced after rounding to 0.033 kWh, it
 * would be 0.00495 EUR, presented as 0.00.
 */
public final class RoundingNoiseDataset {

    /** A day in winter time, in the application time zone. */
    public static final String DATE = "2024/02/10";
    public static final String FIRST_HOUR = "10:00";
    public static final String SECOND_HOUR = "11:00";
    public static final String DAY_START = "2024-02-10T00:00:00+01:00";
    public static final String DAY_END = "2024-02-10T23:00:00+01:00";
    /** The local bounds of February 2024: midnight of day 1 to the last hour of the last day. */
    public static final String MONTH_START = "2024-02-01T00:00:00+01:00";
    public static final String MONTH_END = "2024-02-29T23:00:00+01:00";

    public static final float FIRST_GRID_IMPORT = 0.1234f;
    public static final float FIRST_SELF_CONSUMPTION = 0.0164f;
    public static final float FIRST_SURPLUS = 0.1111f;
    public static final float FIRST_GENERATION = 0.0555f;
    public static final float SECOND_GRID_IMPORT = 0.2222f;
    public static final float SECOND_SELF_CONSUMPTION = 0.0170f;
    public static final float SECOND_SURPLUS = 0.2222f;
    public static final float SECOND_GENERATION = 0.0556f;

    /** The figures of both records summed and rounded once, to 3 decimals. */
    public static final String GRID_IMPORT = "0.346";
    public static final String SELF_CONSUMPTION = "0.033";
    public static final String SURPLUS = "0.333";
    public static final String GENERATION = "0.111";
    /** Grid import plus self-consumption, 0.3790000099..., rounded once. */
    public static final String TOTAL_CONSUMPTION = "0.379";
    /** Self-consumption plus surplus, 0.3667000103..., rounded once. */
    public static final String ASSIGNED_PRODUCTION = "0.367";
    /** 0.0334 kWh at the 0.15 EUR/kWh estimate of the test profile, a constant price for every hour. */
    public static final double SAVINGS_EUR = 0.01;

    private RoundingNoiseDataset() {
    }

    public static DatadisConsumption firstRecord(String cups) {
        return record(cups, FIRST_HOUR, FIRST_GRID_IMPORT, FIRST_SELF_CONSUMPTION, FIRST_SURPLUS, FIRST_GENERATION);
    }

    public static DatadisConsumption secondRecord(String cups) {
        return record(cups, SECOND_HOUR, SECOND_GRID_IMPORT, SECOND_SELF_CONSUMPTION, SECOND_SURPLUS,
                SECOND_GENERATION);
    }

    /** The unrounded sums, computed from the stored figures exactly as the store adds them. */
    public static double gridImportSum() {
        return (double) FIRST_GRID_IMPORT + SECOND_GRID_IMPORT;
    }

    public static double selfConsumptionSum() {
        return (double) FIRST_SELF_CONSUMPTION + SECOND_SELF_CONSUMPTION;
    }

    public static double surplusSum() {
        return (double) FIRST_SURPLUS + SECOND_SURPLUS;
    }

    /**
     * Asserts both hourly records of the supply still carry the figures written, to their last digit.
     */
    public static void assertHourlyRecordsUnchanged(GetDatadisConsumptionRepository repository, Supply supply) {
        assertFigures(storedAt(repository, supply, FIRST_HOUR), FIRST_GRID_IMPORT, FIRST_SELF_CONSUMPTION,
                FIRST_SURPLUS, FIRST_GENERATION);
        assertFigures(storedAt(repository, supply, SECOND_HOUR), SECOND_GRID_IMPORT, SECOND_SELF_CONSUMPTION,
                SECOND_SURPLUS, SECOND_GENERATION);
    }

    /**
     * Asserts the supply's February pre-aggregate still carries the unrounded sums of the figures
     * written: 0.3456, not the 0.346 a response reports.
     */
    public static void assertMonthlyAggregateUnchanged(GetDatadisConsumptionRepository repository, Supply supply,
                                                       float gridImport, float selfConsumption, float surplus,
                                                       float generation) {
        List<DatadisConsumption> stored = repository.getMonthlyConsumptionsByRangeOfDates(supply,
                OffsetDateTime.parse(MONTH_START), OffsetDateTime.parse(MONTH_END));
        assertEquals(1, stored.size(), () -> "stored monthly records: " + stored);
        assertFigures(stored.get(0), gridImport, selfConsumption, surplus, generation);
    }

    /** The hourly read returns every hour of the range, so the written one is picked by its time. */
    private static DatadisConsumption storedAt(GetDatadisConsumptionRepository repository, Supply supply,
                                               String hour) {
        List<DatadisConsumption> stored = repository.getHourlyConsumptionsByRangeOfDates(supply,
                OffsetDateTime.parse(DAY_START), OffsetDateTime.parse(DAY_END));
        return stored.stream()
                .filter(record -> hour.equals(record.getTime()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stored record at " + hour + ": " + stored));
    }

    private static void assertFigures(DatadisConsumption stored, float gridImport, float selfConsumption,
                                      float surplus, float generation) {
        assertEquals(gridImport, stored.getConsumptionKWh());
        assertEquals(selfConsumption, stored.getSelfConsumptionEnergyKWh());
        assertEquals(surplus, stored.getSurplusEnergyKWh());
        assertEquals(generation, stored.getGenerationEnergyKWh());
    }

    private static DatadisConsumption record(String cups, String hour, float gridImport, float selfConsumption,
                                             float surplus, float generation) {
        DatadisConsumption record = hourlyRecord(cups, DATE, hour, gridImport, selfConsumption, surplus);
        record.setGenerationEnergyKWh(generation);
        return record;
    }
}
