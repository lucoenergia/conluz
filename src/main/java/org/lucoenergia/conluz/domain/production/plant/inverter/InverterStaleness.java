package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.time.Duration;
import java.time.Instant;

/**
 * Whether today's inverter data is stale: one threshold for every view of it, so that the same plant is never stale
 * in one and current in another.
 */
public final class InverterStaleness {

    /**
     * The age past which the latest snapshot is stale. A snapshot exactly this old is not.
     */
    public static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private InverterStaleness() {
    }

    /**
     * @param lastSnapshotAt the instant of the latest snapshot of today
     * @param now            the current instant, which its age is measured against
     */
    public static boolean isStale(Instant lastSnapshotAt, Instant now) {
        return Duration.between(lastSnapshotAt, now).compareTo(STALE_AFTER) > 0;
    }
}
