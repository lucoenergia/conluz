package org.lucoenergia.conluz.infrastructure.datadis.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.datadis.GetDatadisConfigurationService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncWindows;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.infrastructure.datadis.DatadisJobTestSupport.enabledConfig;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The job reads today in the JVM's default zone. Every clock is fixed at 12:00Z, so that date is the
 * same in any default zone a test run may have.
 */
class DatadisSyncDailyJobTest {

    private static final UUID COMMUNITY_A = UUID.randomUUID();
    private static final UUID COMMUNITY_B = UUID.randomUUID();

    private final DatadisSyncService syncService = Mockito.mock(DatadisSyncService.class);
    private final GetDatadisConfigurationService configService = Mockito.mock(GetDatadisConfigurationService.class);

    @BeforeEach
    void twoEnabledCommunities() {
        when(configService.findAllEnabled()).thenReturn(List.of(enabledConfig(COMMUNITY_A), enabledConfig(COMMUNITY_B)));
    }

    @Test
    @DisplayName("DHI-007 on a weekday each community is re-read once, from the first day of the month 3 months back through today")
    void aWeekdayReReadsTheLastThreeMonths() {
        LocalDate thursday = LocalDate.of(2026, 11, 26);

        job(noonOf(thursday)).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            verify(syncService).synchronize(community, LocalDate.of(2026, 8, 1), thursday);
        }
        verifyNoMoreInteractions(syncService);
    }

    @Test
    @DisplayName("DHI-007 on a Sunday each community is re-read once, from the first day of the month 24 months back, and not also over the daily window")
    void aSundayReReadsTheLastTwentyFourMonthsOnly() {
        LocalDate sunday = LocalDate.of(2026, 11, 29);

        job(noonOf(sunday)).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            verify(syncService).synchronize(community, LocalDate.of(2024, 11, 1), sunday);
        }
        verifyNoMoreInteractions(syncService);
    }

    @Test
    @DisplayName("DHI-007 on the Monday right after a month change the daily window reaches into the previous year")
    void aMondayAfterAMonthChangeReachesIntoThePreviousYear() {
        LocalDate monday = LocalDate.of(2026, 3, 2);

        job(noonOf(monday)).run();

        for (UUID community : List.of(COMMUNITY_A, COMMUNITY_B)) {
            verify(syncService).synchronize(community, LocalDate.of(2025, 12, 1), monday);
        }
        verifyNoMoreInteractions(syncService);
    }

    @Test
    void aFailingCommunityDoesNotStopTheOthers() {
        LocalDate thursday = LocalDate.of(2026, 11, 26);
        Mockito.doThrow(new RuntimeException("boom"))
                .when(syncService).synchronize(COMMUNITY_A, LocalDate.of(2026, 8, 1), thursday);

        job(noonOf(thursday)).run();

        verify(syncService).synchronize(COMMUNITY_B, LocalDate.of(2026, 8, 1), thursday);
    }

    @Test
    void testRun_ShouldSkipSyncWhenNoEnabledConfigs() {
        when(configService.findAllEnabled()).thenReturn(Collections.emptyList());

        job(noonOf(LocalDate.of(2026, 11, 26))).run();

        verify(syncService, never()).synchronize(any(UUID.class), any(), any());
    }

    private DatadisSyncDailyJob job(Clock clock) {
        return new DatadisSyncDailyJob(syncService, configService, DatadisSyncWindows.DEFAULT, clock);
    }

    private static Clock noonOf(LocalDate date) {
        Instant noon = date.atTime(12, 0).toInstant(ZoneOffset.UTC);
        // The date the job will read: the same in every zone from UTC-11 to UTC+11.
        if (!LocalDate.ofInstant(noon, ZoneId.systemDefault()).equals(date)) {
            throw new IllegalStateException("The JVM default zone moves " + noon + " to another date");
        }
        return Clock.fixed(noon, ZoneOffset.UTC);
    }
}
