package org.lucoenergia.conluz.domain.production.datadis.get;

import java.time.Instant;

/**
 * The instants of the first and the last hourly production record stored for a supply.
 *
 * @param firstRecord the instant the earliest record is stamped at
 * @param lastRecord  the instant the latest record is stamped at
 */
public record RecordedProductionPeriod(Instant firstRecord, Instant lastRecord) {
}
