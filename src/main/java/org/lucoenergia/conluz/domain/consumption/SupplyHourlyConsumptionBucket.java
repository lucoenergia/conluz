package org.lucoenergia.conluz.domain.consumption;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * One hour of a supply's hourly consumption series: the bucket, priced over that hour, together with
 * the instant the hour starts at.
 *
 * <p>The start is what identifies the hour. Its local date and time labels do not on the day the
 * clocks go back, when two consecutive hours carry the same ones.
 */
public class SupplyHourlyConsumptionBucket {

    private final OffsetDateTime startsAt;
    private final SupplyConsumptionBucket bucket;

    private SupplyHourlyConsumptionBucket(OffsetDateTime startsAt, SupplyConsumptionBucket bucket) {
        this.startsAt = startsAt;
        this.bucket = bucket;
    }

    public static SupplyHourlyConsumptionBucket of(OffsetDateTime startsAt, SupplyConsumptionBucket bucket) {
        return new SupplyHourlyConsumptionBucket(Objects.requireNonNull(startsAt), Objects.requireNonNull(bucket));
    }

    /**
     * The instant the hour starts at, with the offset in force in the supply's time zone at that
     * instant.
     */
    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public SupplyConsumptionBucket getBucket() {
        return bucket;
    }

    @Override
    public String toString() {
        return "SupplyHourlyConsumptionBucket{" +
                "startsAt=" + startsAt +
                ", bucket=" + bucket +
                '}';
    }
}
