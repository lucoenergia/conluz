package org.lucoenergia.conluz.domain.consumption.datadis.get;

import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.DatadisTokenRejectedException;

import java.time.Month;
import java.util.List;

public interface GetConsumptionFromDatadisRepository {

    /**
     * The hourly consumption Datadis returns for the supply over one month, requested with the configuration of the
     * supply's community and a token already obtained with it.
     *
     * @return the consumption, or an empty list when Datadis does not answer it
     * @throws DatadisTokenRejectedException when Datadis rejects the token
     */
    List<DatadisConsumption> getHourlyConsumptionsByMonth(DatadisConfig config, String bearerToken, Supply supply,
                                                          Month month, int year);
}
