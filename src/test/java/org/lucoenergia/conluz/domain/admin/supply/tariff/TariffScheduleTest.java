package org.lucoenergia.conluz.domain.admin.supply.tariff;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TariffScheduleTest {

    private static final BigDecimal PRICE = new BigDecimal("0.15");
    private static final BigDecimal VAT = BigDecimal.ZERO;

    private static final TariffSegment JANUARY = segment("2025-01-01", "2025-02-01");
    private static final TariffSegment FEBRUARY = segment("2025-02-01", "2025-03-01");
    private static final TariffSegment MARCH = segment("2025-03-01", "2025-04-01");

    private static TariffSegment segment(String start, String end) {
        return new TariffSegment(
                new DateRange(LocalDate.parse(start), LocalDate.parse(end)),
                TariffPlan.flat(PRICE),
                VAT,
                TariffSource.ESTIMATE
        );
    }

    @Test
    void threeContiguousSegmentsAreAcceptedInOrder() {
        TariffSchedule schedule = new TariffSchedule(List.of(JANUARY, FEBRUARY, MARCH));

        assertEquals(List.of(JANUARY, FEBRUARY, MARCH), schedule.getSegments());
    }

    @Test
    void mutatingTheSourceListAfterConstructionDoesNotAlterTheSchedule() {
        List<TariffSegment> source = new ArrayList<>(List.of(JANUARY, FEBRUARY, MARCH));

        TariffSchedule schedule = new TariffSchedule(source);
        source.clear();

        assertEquals(List.of(JANUARY, FEBRUARY, MARCH), schedule.getSegments());
    }

    @Test
    void outOfOrderSegmentsAreRejected() {
        List<TariffSegment> outOfOrder = List.of(FEBRUARY, JANUARY, MARCH);

        InvalidTariffScheduleException exception =
                assertThrows(InvalidTariffScheduleException.class, () -> new TariffSchedule(outOfOrder));

        assertEquals(InvalidTariffScheduleException.Reason.OUT_OF_ORDER, exception.getReason());
        assertEquals(1, exception.getSegmentIndex());
    }

    @Test
    void overlappingSegmentsAreRejected() {
        List<TariffSegment> overlapping =
                List.of(JANUARY, segment("2025-01-15", "2025-03-01"), MARCH);

        InvalidTariffScheduleException exception =
                assertThrows(InvalidTariffScheduleException.class, () -> new TariffSchedule(overlapping));

        assertEquals(InvalidTariffScheduleException.Reason.OVERLAPPING, exception.getReason());
        assertEquals(1, exception.getSegmentIndex());
    }

    @Test
    void segmentsWithAGapAreRejected() {
        List<TariffSegment> gapped =
                List.of(JANUARY, segment("2025-02-15", "2025-03-01"), MARCH);

        InvalidTariffScheduleException exception =
                assertThrows(InvalidTariffScheduleException.class, () -> new TariffSchedule(gapped));

        assertEquals(InvalidTariffScheduleException.Reason.GAP, exception.getReason());
        assertEquals(1, exception.getSegmentIndex());
    }

    @Test
    void aNullSegmentListIsRejected() {
        InvalidTariffScheduleException exception =
                assertThrows(InvalidTariffScheduleException.class, () -> new TariffSchedule(null));

        assertEquals(InvalidTariffScheduleException.Reason.NULL_SEGMENTS, exception.getReason());
    }

    @Test
    void aNullSegmentIsRejected() {
        List<TariffSegment> withNull = Arrays.asList(JANUARY, null, MARCH);

        InvalidTariffScheduleException exception =
                assertThrows(InvalidTariffScheduleException.class, () -> new TariffSchedule(withNull));

        assertEquals(InvalidTariffScheduleException.Reason.NULL_SEGMENT, exception.getReason());
        assertEquals(1, exception.getSegmentIndex());
    }

    @Test
    void anEmptyScheduleIsRejected() {
        InvalidTariffScheduleException exception = assertThrows(InvalidTariffScheduleException.class,
                () -> new TariffSchedule(Collections.emptyList()));

        assertEquals(InvalidTariffScheduleException.Reason.EMPTY, exception.getReason());
    }
}
