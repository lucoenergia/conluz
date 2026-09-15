package org.lucoenergia.conluz.domain.consumption;

import java.time.Instant;

/**
 * The timestamps of the earliest and the latest hourly consumption record stored for a supply.
 * Both are timestamps of records that exist, so a period built from them is inclusive at both
 * ends.
 */
public class RecordedConsumptionPeriod {

    private final Instant firstRecord;
    private final Instant lastRecord;

    public RecordedConsumptionPeriod(Instant firstRecord, Instant lastRecord) {
        this.firstRecord = firstRecord;
        this.lastRecord = lastRecord;
    }

    public Instant getFirstRecord() {
        return firstRecord;
    }

    public Instant getLastRecord() {
        return lastRecord;
    }
}
