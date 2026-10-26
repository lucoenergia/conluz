package org.lucoenergia.conluz.domain.admin.supply.create;

/**
 * How one row of a supply import ended. {@code code} is the created supply's code for a created row, and the code as
 * the file spelled it otherwise; {@code personalId} is the owner's personal ID as the file spelled it.
 */
public record ImportedSupplyRow(String code, String personalId, SupplyImportOutcome outcome) {
}
