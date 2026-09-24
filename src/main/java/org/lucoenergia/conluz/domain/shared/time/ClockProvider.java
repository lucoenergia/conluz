package org.lucoenergia.conluz.domain.shared.time;

import java.time.Instant;

/**
 * The application's source of "now".
 *
 * <p>A port rather than a direct {@code Instant.now()} call so that a result derived from the
 * current time is reproducible: this is the single point a test replaces to pin it. Anything
 * computed from elapsed time -- a payback rate, a remaining-months estimate -- is untestable
 * otherwise, because its expected value changes with every run.
 *
 * <p>Returns an instant, not a local date or time. Converting to civil time needs a zone, and the
 * zone belongs to the entity being reported on, so it comes from {@link ZoneResolver} at the point
 * of use rather than being baked in here.
 */
public interface ClockProvider {

    Instant now();
}
