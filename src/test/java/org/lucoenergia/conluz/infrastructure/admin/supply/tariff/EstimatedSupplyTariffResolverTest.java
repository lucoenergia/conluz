package org.lucoenergia.conluz.infrastructure.admin.supply.tariff;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.tariff.DateRange;
import org.lucoenergia.conluz.domain.admin.supply.tariff.FlatPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffPlan;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSchedule;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSegment;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TariffSource;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriod;
import org.lucoenergia.conluz.domain.admin.supply.tariff.TaxRatePeriods;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EstimatedSupplyTariffResolverTest {

    private static final BigDecimal BASE_PRICE = new BigDecimal("0.15");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.10");
    private static final BigDecimal ELECTRICITY_TAX_RATE = new BigDecimal("0.025");
    private static final BigDecimal DEFAULT_VAT = new BigDecimal("0.21");
    private static final BigDecimal DEFAULT_ELECTRICITY_TAX = new BigDecimal("0.0511269632");
    private static final SupplyId SUPPLY_ID = SupplyId.of(UUID.randomUUID());

    private final EstimatedSupplyTariffResolver resolver = resolverWith(
            List.of(new TaxRatePeriod(LocalDate.of(2024, 1, 1), null, VAT_RATE)),
            List.of(new TaxRatePeriod(LocalDate.of(2024, 1, 1), null, ELECTRICITY_TAX_RATE)));

    private static EstimatedSupplyTariffResolver resolverWith(List<TaxRatePeriod> vat,
                                                              List<TaxRatePeriod> electricityTax) {
        return new EstimatedSupplyTariffResolver(
                new EstimatedTariffProperties(BASE_PRICE,
                        new EstimatedTariffProperties.Taxes(DEFAULT_VAT, DEFAULT_ELECTRICITY_TAX, List.of(), List.of())),
                new EstimatedTaxRates(new TaxRatePeriods(vat, DEFAULT_VAT),
                        new TaxRatePeriods(electricityTax, DEFAULT_ELECTRICITY_TAX)));
    }

    @Test
    void returnsExactlyOneSegment() {
        DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        assertEquals(1, schedule.getSegments().size());
    }

    @Test
    void sourceIsEstimate() {
        DateRange range = new DateRange(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 7, 1));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        assertEquals(TariffSource.ESTIMATE, schedule.getSegments().get(0).getSource());
    }

    @Test
    void segmentCoversFullRequestedRange() {
        DateRange range = new DateRange(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 4, 15));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        TariffSegment segment = schedule.getSegments().get(0);
        assertEquals(range, segment.getRange());
    }

    @Test
    void segmentCarriesConfiguredBasePriceAsFlatPlan() {
        DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        TariffPlan plan = schedule.getSegments().get(0).getPlan();
        assertInstanceOf(FlatPlan.class, plan);
        assertEquals(0, BASE_PRICE.compareTo(((FlatPlan) plan).getPricePerKwh()));
    }

    @Test
    void segmentCarriesConfiguredVatRate() {
        DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        BigDecimal actualVat = schedule.getSegments().get(0).getVatRate();
        assertEquals(0, VAT_RATE.compareTo(actualVat));
    }

    @Test
    void ignoresSupplyContentAndRange() {
        DateRange range1 = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 7, 1));
        DateRange range2 = new DateRange(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 9, 1));
        SupplyId otherSupply = SupplyId.of(UUID.randomUUID());

        TariffSchedule schedule1 = resolver.scheduleFor(SUPPLY_ID, range1);
        TariffSchedule schedule2 = resolver.scheduleFor(otherSupply, range2);

        assertEquals(1, schedule1.getSegments().size());
        assertEquals(1, schedule2.getSegments().size());
        assertEquals(range1, schedule1.getSegments().get(0).getRange());
        assertEquals(range2, schedule2.getSegments().get(0).getRange());
        assertEquals(0, BASE_PRICE.compareTo(((FlatPlan) schedule2.getSegments().get(0).getPlan()).getPricePerKwh()));
    }

    // --- Dated tax periods (#610) ---

    /**
     * The periods shipped in {@code application.properties}, spelled out so that these tests describe
     * the rules rather than whatever the configuration holds; {@code EstimatedTariffDefaultsTest} pins
     * that the shipped configuration is this.
     */
    private static final List<TaxRatePeriod> SHIPPED_VAT = List.of(
            period("2024-01-01", "2024-03-01", "0.10"),
            period("2024-03-01", "2024-07-01", "0.21"),
            period("2024-07-01", "2025-01-01", "0.10"),
            period("2025-01-01", "2026-03-22", "0.21"),
            period("2026-03-22", "2026-06-01", "0.10"),
            period("2026-06-01", null, "0.21"));
    private static final List<TaxRatePeriod> SHIPPED_ELECTRICITY_TAX = List.of(
            period("2024-01-01", "2024-04-01", "0.025"),
            period("2024-04-01", "2024-07-01", "0.038"),
            period("2024-07-01", "2026-03-22", "0.0511269632"),
            period("2026-03-22", "2026-06-01", "0.005"),
            period("2026-06-01", null, "0.0511269632"));

    private final EstimatedSupplyTariffResolver shippedResolver =
            resolverWith(SHIPPED_VAT, SHIPPED_ELECTRICITY_TAX);

    @Test
    @DisplayName("TRF-002 a range crossing a VAT and an electricity tax boundary on different dates is split at both")
    void aRangeIsSplitAtEveryBoundaryOfEitherTax() {
        TariffSchedule schedule = shippedResolver.scheduleFor(SUPPLY_ID, dateRange("2024-02-01", "2024-05-01"));

        assertEquals(List.of(
                        taxed("2024-02-01", "2024-03-01", "0.10", "0.025"),
                        taxed("2024-03-01", "2024-04-01", "0.21", "0.025"),
                        taxed("2024-04-01", "2024-05-01", "0.21", "0.038")),
                schedule.getSegments());
    }

    @Test
    @DisplayName("TRF-002 a range crossing a date on which both taxes change is split there once")
    void aRangeIsSplitOnceWhereBothTaxesChange() {
        TariffSchedule schedule = shippedResolver.scheduleFor(SUPPLY_ID, dateRange("2026-03-15", "2026-04-01"));

        assertEquals(List.of(
                        taxed("2026-03-15", "2026-03-22", "0.21", "0.0511269632"),
                        taxed("2026-03-22", "2026-04-01", "0.10", "0.005")),
                schedule.getSegments());
    }

    @Test
    @DisplayName("TRF-002 a range inside one period of each tax is a single segment")
    void aRangeInsideOnePeriodIsASingleSegment() {
        TariffSchedule schedule = shippedResolver.scheduleFor(SUPPLY_ID, dateRange("2025-02-01", "2025-03-01"));

        assertEquals(List.of(taxed("2025-02-01", "2025-03-01", "0.21", "0.0511269632")), schedule.getSegments());
    }

    @Test
    @DisplayName("TRF-003 a stretch before the first period is priced at the default rates and logged at WARN")
    void aStretchBeforeTheFirstPeriodIsPricedAtTheDefaults() {
        try (LogCapture logs = LogCapture.of(EstimatedSupplyTariffResolver.class)) {
            TariffSchedule schedule = shippedResolver.scheduleFor(SUPPLY_ID, dateRange("2023-12-01", "2024-02-01"));

            assertEquals(List.of(
                            taxed("2023-12-01", "2024-01-01", "0.21", "0.0511269632"),
                            taxed("2024-01-01", "2024-02-01", "0.10", "0.025")),
                    schedule.getSegments());
            List<String> warnings = warnings(logs);
            assertEquals(2, warnings.size(), () -> "warnings were " + warnings);
            assertTrue(warnings.stream().anyMatch(w -> w.startsWith("No VAT period")
                    && w.contains("[2023-12-01, 2024-01-01)")), () -> "warnings were " + warnings);
            assertTrue(warnings.stream().anyMatch(w -> w.startsWith("No electricity tax period")
                    && w.contains("[2023-12-01, 2024-01-01)")), () -> "warnings were " + warnings);
        }
    }

    @Test
    @DisplayName("TRF-003 a range every period covers logs nothing")
    void aCoveredRangeLogsNothing() {
        try (LogCapture logs = LogCapture.of(EstimatedSupplyTariffResolver.class)) {
            shippedResolver.scheduleFor(SUPPLY_ID, dateRange("2026-11-01", "2026-12-01"));

            assertEquals(List.of(), warnings(logs));
        }
    }

    /**
     * The hourly and daily series ask for one day at a time, so a long period before the first
     * configured period would otherwise repeat the warning once per day.
     */
    @Test
    @DisplayName("TRF-003 an uncovered stretch is logged once per tax, and another stretch logs again")
    void anUncoveredStretchIsLoggedOncePerTax() {
        EstimatedSupplyTariffResolver closedVat = resolverWith(
                List.of(period("2024-01-01", "2025-01-01", "0.10")),
                List.of(period("2024-01-01", null, "0.025")));

        try (LogCapture logs = LogCapture.of(EstimatedSupplyTariffResolver.class)) {
            closedVat.scheduleFor(SUPPLY_ID, dateRange("2023-12-01", "2023-12-02"));
            assertEquals(2, warnings(logs).size(), () -> "warnings were " + warnings(logs));

            closedVat.scheduleFor(SUPPLY_ID, dateRange("2023-12-01", "2023-12-02"));
            closedVat.scheduleFor(SUPPLY_ID, dateRange("2023-12-02", "2023-12-03"));
            assertEquals(2, warnings(logs).size(), () -> "warnings were " + warnings(logs));

            TariffSchedule after = closedVat.scheduleFor(SUPPLY_ID, dateRange("2025-02-01", "2025-02-02"));
            List<String> warnings = warnings(logs);
            assertEquals(3, warnings.size(), () -> "warnings were " + warnings);
            assertTrue(warnings.get(2).startsWith("No VAT period is configured from 2025-01-01 on"),
                    () -> "warnings were " + warnings);
            // Pricing is unchanged by the de-duplication: still the default VAT.
            assertEquals(List.of(taxed("2025-02-01", "2025-02-02", "0.21", "0.025")), after.getSegments());
        }
    }

    private static List<String> warnings(LogCapture logs) {
        return logs.warningsAndAbove().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static TaxRatePeriod period(String from, String to, String rate) {
        return new TaxRatePeriod(LocalDate.parse(from), to == null ? null : LocalDate.parse(to), new BigDecimal(rate));
    }

    private static DateRange dateRange(String start, String end) {
        return new DateRange(LocalDate.parse(start), LocalDate.parse(end));
    }

    private static TariffSegment taxed(String start, String end, String vatRate, String electricityTaxRate) {
        return new TariffSegment(dateRange(start, end), TariffPlan.flat(BASE_PRICE), new BigDecimal(vatRate),
                new BigDecimal(electricityTaxRate), TariffSource.ESTIMATE);
    }
}
