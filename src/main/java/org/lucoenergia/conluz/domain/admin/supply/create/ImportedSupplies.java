package org.lucoenergia.conluz.domain.admin.supply.create;

import java.util.List;

/**
 * The outcome of importing a file of supplies: one row per data row of the file, in its order.
 */
public record ImportedSupplies(List<ImportedSupplyRow> rows) {

    public ImportedSupplies {
        rows = List.copyOf(rows);
    }

    public int created() {
        return (int) rows.stream().filter(ImportedSupplyRow::isCreated).count();
    }

    public int failed() {
        return rows.size() - created();
    }
}
