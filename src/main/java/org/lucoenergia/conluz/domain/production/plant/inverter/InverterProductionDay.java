package org.lucoenergia.conluz.domain.production.plant.inverter;

import java.util.List;

/**
 * What is stored for a plant's inverter around the current local day: the {@code day_power} of the last snapshot of
 * the previous local day, or null when there is none, and the snapshots of the current local day, in time order.
 */
public record InverterProductionDay(Double previousDayLastKWh, List<InverterSnapshot> todaySnapshots) {
}
