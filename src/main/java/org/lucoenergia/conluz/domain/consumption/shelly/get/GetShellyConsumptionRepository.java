package org.lucoenergia.conluz.domain.consumption.shelly.get;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;

import java.time.OffsetDateTime;
import java.util.List;

public interface GetShellyConsumptionRepository {

    List<ShellyInstantConsumption> getHourlyConsumptionsByRangeOfDatesAndSupply(@NotNull Supply supply, @NotNull OffsetDateTime startDate,
                                                                                @NotNull OffsetDateTime endDate);

    List<ShellyInstantConsumption> getAllInstantConsumptions();

    List<ShellyConsumption> getAllConsumptions();

    List<ShellyInstantConsumption> getShellyMqttPowerMessagesByRangeOfDates(OffsetDateTime startDate,
                                                                            OffsetDateTime endDate);
}
