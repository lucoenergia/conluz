package org.lucoenergia.conluz.domain.production.huawei.sync;

import java.time.Period;
import java.util.Objects;

/**
 * How far back the daily Huawei sync of previous days re-reads hourly records: every day from
 * {@link #lookback()} before today through yesterday, both inclusive. Today itself is synced by
 * its own hourly job.
 *
 * <p>The single definition of that window: anything that has to cover the same period as the sync
 * derives it from here rather than restating it.
 *
 * @param lookback how far back from today the first synced day is
 */
public record HuaweiSyncWindow(Period lookback) {

    public static final HuaweiSyncWindow DEFAULT = new HuaweiSyncWindow(Period.ofDays(7));

    public HuaweiSyncWindow {
        Objects.requireNonNull(lookback, "lookback");
        if (lookback.isNegative()) {
            throw new IllegalArgumentException("The sync window lookback cannot be negative: " + lookback);
        }
    }
}
