package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.*;

class SyncDailyPricesJobTest {

    private final SyncDailyPricesService mockSyncDailyPricesService = Mockito.mock(SyncDailyPricesService.class);

    /**
     * The job syncs the last week of market days, today included and tomorrow not, with today read in Europe/Madrid
     * whatever zone the clock carries: near midnight the clock's own date is a different day.
     */
    @ParameterizedTest(name = "at {0} with a clock in {1}, it syncs [{2}, {3})")
    @CsvSource({
            // 06:00 in Madrid, when the job is scheduled
            "2026-10-19T04:00:00Z, Europe/Madrid, 2026-10-12, 2026-10-20",
            // 00:30 on the 19th in Madrid, still the 18th in UTC and in New York
            "2026-10-18T22:30:00Z, UTC, 2026-10-12, 2026-10-20",
            "2026-10-18T22:30:00Z, America/New_York, 2026-10-12, 2026-10-20",
            // 23:30 on the 25th in Madrid, already the 26th in Tokyo
            "2026-10-25T22:30:00Z, Asia/Tokyo, 2026-10-18, 2026-10-26",
            // 00:30 on the 26th in Madrid, the first night after the clocks go back
            "2026-10-25T23:30:00Z, UTC, 2026-10-19, 2026-10-27",
            // 00:30 on the 30th in Madrid, the first night after the clocks go forward
            "2026-03-29T22:30:00Z, UTC, 2026-03-23, 2026-03-31"
    })
    void testRunSyncsTheWeekUpToTodayInMadrid(Instant now, String clockZone, LocalDate expectedStart,
                                              LocalDate expectedEnd) {
        SyncDailyPricesJob job = new SyncDailyPricesJob(mockSyncDailyPricesService,
                Clock.fixed(now, ZoneId.of(clockZone)));

        job.run();

        verify(mockSyncDailyPricesService, times(1)).syncDailyPricesByDays(expectedStart, expectedEnd);
        verifyNoMoreInteractions(mockSyncDailyPricesService);
    }
}
