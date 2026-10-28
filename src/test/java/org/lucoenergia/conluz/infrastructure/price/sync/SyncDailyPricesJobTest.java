package org.lucoenergia.conluz.infrastructure.price.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.domain.price.PriceByHour;
import org.lucoenergia.conluz.domain.price.persist.PersistOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.GetOmiePricesRepository;
import org.lucoenergia.conluz.domain.price.sync.OmieDayPrices;
import org.lucoenergia.conluz.domain.price.sync.PriceSyncPeriodValidator;
import org.lucoenergia.conluz.domain.price.sync.SyncDailyPricesService;
import org.lucoenergia.conluz.infrastructure.price.OmieConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class SyncDailyPricesJobTest {

    private final SyncDailyPricesService mockSyncDailyPricesService = Mockito.mock(SyncDailyPricesService.class);

    private TimeZone originalDefaultZone;

    @BeforeEach
    void saveDefaultZone() {
        originalDefaultZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreDefaultZone() {
        TimeZone.setDefault(originalDefaultZone);
    }

    /**
     * The schedule is resolved as {@code ScheduledAnnotationBeanPostProcessor} resolves it: the annotation's zone, or
     * the JVM default zone when it has none. With the JVM default set to UTC, a cron without a zone would run at 21:00
     * UTC and fail. Each span crosses a clock change, where 21:00 in Madrid moves by an hour in UTC.
     */
    @ParameterizedTest(name = "from {0}, the next runs are at {1}, {2} and {3}")
    @CsvSource({
            // The clocks go forward on 2026-03-29
            "2026-03-27T12:00:00Z, 2026-03-27T20:00:00Z, 2026-03-28T20:00:00Z, 2026-03-29T19:00:00Z",
            // The clocks go back on 2026-10-25
            "2026-10-23T12:00:00Z, 2026-10-23T19:00:00Z, 2026-10-24T19:00:00Z, 2026-10-25T20:00:00Z"
    })
    @DisplayName("PRC-008 runs once a day at 21:00 Europe/Madrid whatever the JVM zone, across a clock change")
    @ResourceLock(Resources.TIME_ZONE)
    void testRunIsScheduledDailyAt21InMadrid(Instant from, Instant first, Instant second, Instant third)
            throws NoSuchMethodException {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(
                SyncDailyPricesJob.class.getMethod("run"), Scheduled.class);
        assertNotNull(scheduled);
        assertEquals(OmieConfig.OMIE_ZONE.getId(), scheduled.zone());
        TimeZone zone = StringUtils.hasText(scheduled.zone())
                ? StringUtils.parseTimeZoneString(scheduled.zone())
                : TimeZone.getDefault();
        CronTrigger trigger = new CronTrigger(scheduled.cron(), zone.toZoneId());

        List<Instant> executions = new ArrayList<>();
        SimpleTriggerContext context = new SimpleTriggerContext(Clock.fixed(from, ZoneOffset.UTC));
        for (int i = 0; i < 3; i++) {
            Instant next = trigger.nextExecution(context);
            executions.add(next);
            context = new SimpleTriggerContext(next, next, next);
        }

        assertEquals(List.of(first, second, third), executions);
        executions.forEach(execution -> assertEquals(LocalTime.of(21, 0),
                execution.atZone(OmieConfig.OMIE_ZONE).toLocalTime()));
    }

    /**
     * The job syncs nine market days, from seven days ago to tomorrow, with today read in Europe/Madrid whatever zone
     * the clock carries: near midnight the clock's own date is a different day. The range is one the service accepts.
     */
    @ParameterizedTest(name = "at {0} with a clock in {1}, it syncs [{2}, {3})")
    @CsvSource({
            // 21:00 in Madrid in summer time, the same date in UTC
            "2026-10-19T19:00:00Z, UTC, 2026-10-12, 2026-10-21",
            // 21:00 in Madrid in winter time, the same date in UTC
            "2026-11-18T20:00:00Z, UTC, 2026-11-11, 2026-11-20",
            // 21:00 in Madrid on the day the clocks go forward
            "2026-03-29T19:00:00Z, UTC, 2026-03-22, 2026-03-31",
            // 21:00 in Madrid on the day the clocks go back
            "2026-10-25T20:00:00Z, UTC, 2026-10-18, 2026-10-27",
            // 00:30 on the 19th in Madrid, still the 18th in UTC
            "2026-11-18T23:30:00Z, UTC, 2026-11-12, 2026-11-21",
            // 00:30 on the 19th in Madrid, still the 18th in New York
            "2026-10-18T22:30:00Z, America/New_York, 2026-10-12, 2026-10-21",
            // 23:30 on the 25th in Madrid, already the 26th in Tokyo
            "2026-10-25T22:30:00Z, Asia/Tokyo, 2026-10-18, 2026-10-27"
    })
    @DisplayName("PRC-008 syncs the market days from seven days ago to tomorrow in Europe/Madrid")
    void testRunSyncsFromAWeekAgoToTomorrowInMadrid(Instant now, String clockZone, LocalDate expectedStart,
                                                     LocalDate expectedEnd) {
        Clock clock = Clock.fixed(now, ZoneId.of(clockZone));
        SyncDailyPricesJob job = new SyncDailyPricesJob(mockSyncDailyPricesService, clock);

        job.run();

        verify(mockSyncDailyPricesService, times(1)).syncDailyPricesByDays(expectedStart, expectedEnd);
        verifyNoMoreInteractions(mockSyncDailyPricesService);
        assertEquals(9, ChronoUnit.DAYS.between(expectedStart, expectedEnd));
        assertDoesNotThrow(() -> PriceSyncPeriodValidator.validate(expectedStart, expectedEnd,
                LocalDate.now(clock.withZone(OmieConfig.OMIE_ZONE))));
    }

    @Test
    @DisplayName("PRC-008 tomorrow not yet published is skipped and the other eight days are stored")
    void testRunStoresTheOtherDaysWhenTomorrowIsNotPublished() {
        GetOmiePricesRepository getOmiePricesRepository = mock(GetOmiePricesRepository.class);
        PersistOmiePricesRepository persistOmiePricesRepository = mock(PersistOmiePricesRepository.class);
        // 21:00 on 2026-10-19 in Madrid
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T19:00:00Z"), ZoneOffset.UTC);
        SyncDailyPricesJob job = new SyncDailyPricesJob(
                new SyncDailyPricesServiceImpl(getOmiePricesRepository, persistOmiePricesRepository, clock), clock);
        LocalDate today = LocalDate.parse("2026-10-19");
        List<OmieDayPrices> days = new ArrayList<>();
        List<PriceByHour> servedPrices = new ArrayList<>();
        for (LocalDate day = today.minusDays(7); !day.isAfter(today); day = day.plusDays(1)) {
            PriceByHour price = new PriceByHour(0.1,
                    day.atStartOfDay(OmieConfig.OMIE_ZONE).toOffsetDateTime());
            servedPrices.add(price);
            days.add(OmieDayPrices.served(day, List.of(price)));
        }
        days.add(OmieDayPrices.notServed(today.plusDays(1), OmieDayPrices.Status.NOT_FOUND));
        when(getOmiePricesRepository.getPricesByDays(today.minusDays(7), today.plusDays(2))).thenReturn(days);

        assertDoesNotThrow(job::run);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PriceByHour>> stored = ArgumentCaptor.forClass(List.class);
        verify(persistOmiePricesRepository, times(1)).persistPrices(stored.capture());
        assertEquals(8, stored.getValue().size());
        assertEquals(servedPrices, stored.getValue());
    }
}
