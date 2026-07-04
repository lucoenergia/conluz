package org.lucoenergia.conluz.infrastructure.production.datadis;

import org.lucoenergia.conluz.domain.consumption.datadis.DatadisConsumption;
import org.lucoenergia.conluz.domain.production.datadis.DatadisProduction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Derives {@link DatadisProduction} from the surplus energy of a {@link DatadisConsumption}.
 * For a plant supply the surplus reported by Datadis is the energy it produced and exported, so it
 * becomes the production value. A {@code null} surplus is mapped to {@code 0f}.
 */
@Component
public class DatadisProductionMapper {

    public DatadisProduction map(DatadisConsumption consumption) {
        DatadisProduction production = new DatadisProduction();
        production.setCups(consumption.getCups());
        production.setDate(consumption.getDate());
        production.setTime(consumption.getTime());
        production.setObtainMethod(consumption.getObtainMethod());
        Float surplus = consumption.getSurplusEnergyKWh();
        production.setProductionKWh(surplus == null ? 0f : surplus);
        return production;
    }

    public List<DatadisProduction> mapList(List<DatadisConsumption> consumptions) {
        return consumptions.stream()
                .map(this::map)
                .collect(Collectors.toList());
    }
}
