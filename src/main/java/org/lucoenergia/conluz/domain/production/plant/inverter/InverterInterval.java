package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Duration;
import java.time.Instant;

/**
 * The interval between two consecutive inverter snapshots, and the energy the inverter counted in it, in kWh,
 * never negative. An interval longer than {@link #MAX_INTERVAL} is a gap: its energy is known, but how it was spread
 * over the interval is not.
 */
public record InverterInterval(Instant from, Instant to, double energyKWh) {

    /**
     * The longest interval between two snapshots that is still averaged. A single missing snapshot, about 10
     * minutes, is averaged; more is a gap.
     */
    public static final Duration MAX_INTERVAL = Duration.ofMinutes(15);

    public Duration duration() {
        return Duration.between(from, to);
    }

    /**
     * Whether the interval is longer than {@link #MAX_INTERVAL}. An interval exactly that long is not a gap.
     */
    public boolean isGap() {
        return duration().compareTo(MAX_INTERVAL) > 0;
    }
}
