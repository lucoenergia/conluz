package org.lucoenergia.conluz.domain.consumption.shelly.persist;

import jakarta.validation.constraints.NotNull;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;

import java.util.List;

public interface PersistShellyConsumptionRepository {

    void persistInstantConsumptions(@NotNull List<ShellyInstantConsumption> consumptions);

    void persistConsumptions(@NotNull List<ShellyConsumption> consumptions);
}
