package org.lucoenergia.conluz.infrastructure.consumption.shelly.parse;

import org.lucoenergia.conluz.domain.consumption.shelly.ShellyInstantConsumption;
import org.lucoenergia.conluz.domain.consumption.shelly.get.GetShellyConsumptionRepository;
import org.lucoenergia.conluz.domain.consumption.shelly.persist.PersistShellyConsumptionRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class ShellyMqttPowerMessagesToInstantConsumptionsProcessor {

    private final GetShellyConsumptionRepository getShellyConsumptionRepository;
    private final PersistShellyConsumptionRepository persistShellyConsumptionRepository;

    public ShellyMqttPowerMessagesToInstantConsumptionsProcessor(
            GetShellyConsumptionRepository getShellyConsumptionRepository,
            PersistShellyConsumptionRepository persistShellyConsumptionRepository) {
        this.getShellyConsumptionRepository = getShellyConsumptionRepository;
        this.persistShellyConsumptionRepository = persistShellyConsumptionRepository;
    }

    public void process(OffsetDateTime startDate, OffsetDateTime endDate) {

        List<ShellyInstantConsumption> instantConsumptions = getShellyConsumptionRepository.getShellyMqttPowerMessagesByRangeOfDates(
                startDate, endDate);

        persistShellyConsumptionRepository.persistInstantConsumptions(instantConsumptions);
    }
}
