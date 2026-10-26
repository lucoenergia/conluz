package org.lucoenergia.conluz.domain.admin.supply.create;

import org.lucoenergia.conluz.domain.admin.supply.Supply;

/**
 * One row of a supply import: the supply to create, and the code and owner's personal ID as the file spelled them.
 */
public record SupplyImportRow(Supply supply, String code, String personalId) {
}
