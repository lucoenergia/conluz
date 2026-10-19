package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.util.GregorianCalendar;

/**
 * Reads the JVM clock through the no-argument {@link GregorianCalendar} constructor.
 */
public class ConstructsGregorianCalendarOfNow {

    GregorianCalendar now() {
        return new GregorianCalendar();
    }
}
