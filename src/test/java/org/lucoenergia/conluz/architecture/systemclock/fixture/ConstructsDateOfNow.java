package org.lucoenergia.conluz.architecture.systemclock.fixture;

import java.util.Date;

/**
 * Reads the JVM clock through the no-argument {@link Date} constructor.
 */
public class ConstructsDateOfNow {

    Date now() {
        return new Date();
    }
}
