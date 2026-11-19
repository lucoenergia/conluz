package org.lucoenergia.conluz.domain.consumption;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * The monthly form of ENM-001: whether a month is published for a supply, given the hours it holds there,
 * and which months are candidates for the latest published one.
 *
 * <p>What counts as an hour is the caller's: a record carrying self-consumption for a consumer supply, a
 * production or legacy surplus record for a plant's own supply (ENM-009).</p>
 */
public final class MonthlyPublication {

    private MonthlyPublication() {
    }

    /**
     * The earliest month searched when {@code currentMonth} is the current one.
     */
    public static YearMonth firstCandidate(YearMonth currentMonth) {
        return currentMonth.minusMonths(ReferenceMonthResolver.SEARCH_WINDOW_MONTHS);
    }

    /**
     * Whether {@code hours} reach the minimum share of the hours the supply could hold in the month: from the
     * later of the month's first hour and its first record, to the end of the month, counted on the instant line
     * of {@code zone}.
     */
    public static boolean isPublished(YearMonth month, Instant firstRecord, long hours, ZoneId zone) {
        Instant monthStart = startOf(month, zone);
        Instant monthEnd = startOf(month.plusMonths(1), zone);
        Instant firstPossibleHour = firstRecord.isAfter(monthStart) ? firstRecord : monthStart;
        long possibleHours = Duration.between(firstPossibleHour, monthEnd).toHours();
        return PublicationThreshold.isReached(hours, possibleHours);
    }

    /**
     * The latest month before {@code currentMonth}, within the search window and not before the month of
     * {@code firstRecord}, whose hours are published; empty when none is.
     *
     * @param hoursByMonth the supply's hours per month; a month absent holds none
     */
    public static Optional<YearMonth> latestPublished(Map<YearMonth, Long> hoursByMonth, Instant firstRecord,
                                                      YearMonth currentMonth, ZoneId zone) {
        YearMonth firstRecordMonth = YearMonth.from(firstRecord.atZone(zone));
        YearMonth earliest = firstCandidate(currentMonth);
        if (firstRecordMonth.isAfter(earliest)) {
            earliest = firstRecordMonth;
        }
        for (YearMonth month = currentMonth.minusMonths(1); !month.isBefore(earliest); month = month.minusMonths(1)) {
            if (isPublished(month, firstRecord, hoursByMonth.getOrDefault(month, 0L), zone)) {
                return Optional.of(month);
            }
        }
        return Optional.empty();
    }

    private static Instant startOf(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }
}
