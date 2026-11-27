package org.lucoenergia.conluz.infrastructure.admin.supply.consumption;

import io.swagger.v3.oas.annotations.media.Schema;
import org.lucoenergia.conluz.domain.consumption.datadis.get.SupplyDatadisConsumptionMonth;

/**
 * A calendar month holding at least one of a supply's stored hourly Datadis records. Below
 * {@code expectedHours}, the month is incomplete.
 */
@Schema(requiredProperties = {"month", "hoursWithData", "expectedHours"})
public class SupplyDatadisConsumptionMonthResponse {

    @Schema(description = "The calendar month, as YYYY-MM. It is the month getSupplyDatadisConsumptionHourlyCsv " +
            "exports with the same value.",
            type = "string", pattern = "^\\d{4}-(0[1-9]|1[0-2])$", example = "2026-09")
    private final String month;
    @Schema(description = "Number of hourly consumption records whose hour starts in the month: the lines, after " +
            "the header, of the month's getSupplyDatadisConsumptionHourlyCsv export.", example = "312")
    private final long hoursWithData;
    @Schema(description = "Number of hours the whole month spans, the current month included. Accounts for " +
            "daylight saving transitions, so a transition day contributes 23 or 25 hours rather than 24.",
            example = "720")
    private final long expectedHours;

    public SupplyDatadisConsumptionMonthResponse(SupplyDatadisConsumptionMonth month) {
        this.month = month.month().toString();
        this.hoursWithData = month.hoursWithData();
        this.expectedHours = month.expectedHours();
    }

    public String getMonth() {
        return month;
    }

    public long getHoursWithData() {
        return hoursWithData;
    }

    public long getExpectedHours() {
        return expectedHours;
    }
}
