package org.lucoenergia.conluz.architecture.systemclock.fixture;

/**
 * Reads the JVM clock through {@link System#currentTimeMillis()}.
 */
public class ReadsCurrentTimeMillis {

    long now() {
        return System.currentTimeMillis();
    }
}
