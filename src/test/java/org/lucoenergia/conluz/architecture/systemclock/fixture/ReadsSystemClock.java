package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Obtains a clock that follows the JVM clock, in each of the three ways {@link Clock} offers.
 */
public class ReadsSystemClock {

    void obtainTheSystemClock() {
        Clock.systemUTC();
        Clock.systemDefaultZone();
        Clock.system(ZoneId.of("Europe/Madrid"));
    }
}
