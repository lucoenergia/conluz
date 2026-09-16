package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.util.UUID;

/**
 * The identifying and display data of a plant, as carried by
 * {@link SupplyPartitionCoefficientDetail}. Not the {@code Plant} aggregate, for the reason given on
 * {@link SupplyReference}.
 */
public record PlantReference(UUID id, String name) {
}
