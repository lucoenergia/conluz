package org.lucoenergia.conluz.domain.consumption.datadis.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.shared.SupplyId;

import java.util.List;

/**
 * Lists the calendar months holding one supply's stored hourly Datadis records.
 */
public interface GetSupplyDatadisConsumptionMonthsService {

    /**
     * Lists every calendar month, in the application's time zone, holding at least one of the supply's stored
     * hourly Datadis records, newest first, without a lower or upper bound. A month counts the same records the
     * supply's monthly CSV export writes for it. A disabled supply still has every month listed.
     *
     * @param supplyId the supply whose months are listed
     * @return the months, newest first, empty when the supply has no record
     * @throws org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException when the supply does not exist
     */
    List<SupplyDatadisConsumptionMonth> getHourlyMonths(@NotNull SupplyId supplyId);
}
