package org.lucoenergia.conluz.domain.datadis.sync;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Period;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatadisSyncWindowsTest {

    private static final DatadisSyncWindow DAILY = new DatadisSyncWindow(Period.ofMonths(2));
    private static final DatadisSyncWindow FULL = new DatadisSyncWindow(Period.ofMonths(9));
    private static final DatadisSyncWindows WINDOWS = new DatadisSyncWindows(DAILY, FULL);

    @Test
    void theFullWindowIsSyncedOnSundaysAndTheDailyOneOnEveryOtherDay() {
        // Monday 2026-11-23 through Saturday 2026-11-28.
        for (int day = 23; day <= 28; day++) {
            LocalDate date = LocalDate.of(2026, 11, day);
            assertSame(DAILY, WINDOWS.forSyncOn(date), date.toString());
        }
        LocalDate sunday = LocalDate.of(2026, 11, 29);
        assertEquals(DayOfWeek.SUNDAY, sunday.getDayOfWeek());
        assertSame(FULL, WINDOWS.forSyncOn(sunday));
    }

    @Test
    void bothWindowsAreRequired() {
        assertThrows(NullPointerException.class, () -> new DatadisSyncWindows(null, FULL));
        assertThrows(NullPointerException.class, () -> new DatadisSyncWindows(DAILY, null));
    }
}
