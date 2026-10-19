package org.lucoenergia.conluz.infrastructure.shared.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that only moves when a test moves it.
 * <p>
 * {@link #withZone} returns a view in another zone over the same instant, so a clock derived from this one still
 * sees every later {@link #advance} or {@link #setInstant}, whichever of them it is called on.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(new AtomicReference<>(instant), ZoneOffset.UTC);
    }

    private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    public void setInstant(Instant instant) {
        this.instant.set(instant);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return zone.equals(this.zone) ? this : new MutableClock(instant, zone);
    }
}
