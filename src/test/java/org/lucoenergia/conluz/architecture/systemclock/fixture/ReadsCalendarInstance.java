package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Reads the JVM clock through every overload of {@code Calendar.getInstance}, including one qualified by a subclass.
 */
public class ReadsCalendarInstance {

    @SuppressWarnings("static-access")
    void readTheCalendar() {
        TimeZone zone = TimeZone.getTimeZone("Europe/Madrid");
        Calendar.getInstance();
        Calendar.getInstance(zone);
        Calendar.getInstance(Locale.ROOT);
        Calendar.getInstance(zone, Locale.ROOT);
        GregorianCalendar.getInstance();
    }
}
