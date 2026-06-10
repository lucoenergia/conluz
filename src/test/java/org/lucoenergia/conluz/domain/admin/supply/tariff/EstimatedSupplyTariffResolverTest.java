package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.EstimatedSupplyTariffResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EstimatedSupplyTariffResolverTest {

    private static final BigDecimal BASE_PRICE = new BigDecimal("0.15");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.21");
    private static final SupplyId SUPPLY_ID = SupplyId.of(UUID.randomUUID());

    private final EstimatedSupplyTariffResolver resolver = new EstimatedSupplyTariffResolver(BASE_PRICE, VAT_RATE);

    @Test
    void returnsExactlyOneSegment() {
        DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

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
        DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        TariffPlan plan = schedule.getSegments().get(0).getPlan();
        assertInstanceOf(FlatPlan.class, plan);
        assertEquals(0, BASE_PRICE.compareTo(((FlatPlan) plan).getPricePerKwh()));
    }

    @Test
    void segmentCarriesConfiguredVatRate() {
        DateRange range = new DateRange(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        TariffSchedule schedule = resolver.scheduleFor(SUPPLY_ID, range);

        BigDecimal actualVat = schedule.getSegments().get(0).getVatRate();
        assertEquals(0, VAT_RATE.compareTo(actualVat));
    }

    @Test
    void ignoresSupplyContentAndRange() {
        DateRange range1 = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30));
        DateRange range2 = new DateRange(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 8, 31));
        SupplyId otherSupply = SupplyId.of(UUID.randomUUID());

        TariffSchedule schedule1 = resolver.scheduleFor(SUPPLY_ID, range1);
        TariffSchedule schedule2 = resolver.scheduleFor(otherSupply, range2);

        assertEquals(1, schedule1.getSegments().size());
        assertEquals(1, schedule2.getSegments().size());
        assertEquals(range1, schedule1.getSegments().get(0).getRange());
        assertEquals(range2, schedule2.getSegments().get(0).getRange());
        assertEquals(0, BASE_PRICE.compareTo(((FlatPlan) schedule2.getSegments().get(0).getPlan()).getPricePerKwh()));
    }
}
