package org.lucoenergia.conluz.domain.consumption.datadis.get;

import java.time.YearMonth;

/**
 * A calendar month holding at least one of a supply's stored hourly Datadis records, with how complete it is.
 *
 * @param month         the local calendar month
 * @param hoursWithData the number of stored records whose hour starts in the month
 * @param expectedHours the number of hours the whole month spans, accounting for daylight saving transitions
 */
public record SupplyDatadisConsumptionMonth(YearMonth month, long hoursWithData, long expectedHours) {
}
