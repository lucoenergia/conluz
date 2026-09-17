package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.util.UUID;

/**
 * The identifying and display data of a community, as carried by
 * {@link SupplyPartitionCoefficientDetail}. Not the {@code Community} aggregate, for the reason
 * given on {@link SupplyReference}.
 */
public record CommunityReference(UUID id, String name) {
}
