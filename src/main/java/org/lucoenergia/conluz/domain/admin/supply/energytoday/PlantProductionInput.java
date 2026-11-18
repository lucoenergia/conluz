package org.lucoenergia.conluz.domain.admin.supply.energytoday;

import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.CoefficientSegment;
import org.lucoenergia.conluz.domain.production.plant.inverter.InverterSnapshotIntervals;

import java.util.List;

/**
 * What one plant contributes to a supply point's production of today: its inverter snapshots of the current local
 * day, and the supply's coefficient segments in that plant over the day, half-open and in time order.
 */
public record PlantProductionInput(InverterSnapshotIntervals snapshots, List<CoefficientSegment> segments) {
}
