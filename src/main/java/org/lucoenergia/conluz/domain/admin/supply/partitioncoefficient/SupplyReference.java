package org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient;

import java.util.UUID;

/**
 * The identifying and display data of a supply, as carried by
 * {@link SupplyPartitionCoefficientDetail}.
 *
 * <p>Deliberately not the {@code Supply} aggregate: mapping one dereferences its user, community,
 * shelly, distributor and contract associations, three of which are {@code @OneToOne(mappedBy)} and
 * therefore eager and unproxyable. Loading that per coefficient row would cost several extra queries
 * per row.
 *
 * @param name may be null -- supplies.name is nullable.
 */
public record SupplyReference(UUID id, String code, String name) {
}
