package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.time.YearMonth;

/**
 * Writes one supply's stored hourly Datadis records as a CSV file.
 */
public interface GetSupplyDatadisConsumptionCsvService {

    /**
     * Writes the supply's stored hourly Datadis records whose hour starts inside the calendar month, in the
     * application's time zone, in chronological order. A disabled supply still has every stored record
     * written.
     *
     * @param supplyId the supply whose records are written
     * @param month    the calendar month, the current one at the latest
     * @return the file, with the header only when the month has no record
     * @throws ConsumptionCsvMonthInFutureException when the month is after the current one
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException when the supply does not exist
     */
    SupplyDatadisConsumptionCsv getHourlyCsv(@NotNull SupplyId supplyId, @NotNull YearMonth month);
}
