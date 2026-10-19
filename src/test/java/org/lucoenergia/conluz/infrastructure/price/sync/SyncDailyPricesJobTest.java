package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.shared.time.TimeConfiguration;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.mockito.Mockito.*;

class SyncDailyPricesJobTest {

    private static final Instant NOW = Instant.parse("2026-10-19T04:00:00Z");

    private final TimeConfiguration mockTimeConfiguration = Mockito.mock(TimeConfiguration.class);
    private final SyncDailyPricesService mockSyncDailyPricesService = Mockito.mock(SyncDailyPricesService.class);
    private final SyncDailyPricesJob job = new SyncDailyPricesJob(mockTimeConfiguration, mockSyncDailyPricesService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void testRunSyncsTheWeekUpToNowInTheConfiguredZone() {
        when(mockTimeConfiguration.getZoneId()).thenReturn(ZoneId.of("Europe/Madrid"));

        job.run();

        verify(mockSyncDailyPricesService, times(1)).syncDailyPricesByDateInterval(
                OffsetDateTime.parse("2026-10-12T06:00:00+02:00"), OffsetDateTime.parse("2026-10-19T06:00:00+02:00"));
    }
}
