package org.lucoenergia.conluz.domain.admin.supply.create;

import java.util.UUID;

/**
 * Creates the supplies listed in a CSV file, each one as {@link CreateSupplyService} does (SUP-003). The whole file is
 * read and checked before any row is created; then each row is created in a transaction of its own, so a row that
 * fails keeps the rows created before and after it.
 *
 * <p>A row's outcome carries nothing the file held: neither its CUPS nor its personal ID.</p>
 */
public interface ImportSuppliesService {

    /**
     * The most data rows one file may hold, so that one request stays bounded.
     */
    int MAX_ROWS = 1000;

    /**
     * @param csv the file's content: UTF-8, optionally with a byte order mark, or Windows-1252, separated by
     *            {@code ,} or {@code ;}
     * @return the outcome of each data row, in the order of the file
     * @throws SupplyImportFileRefusedException if the file as a whole cannot be imported. Checked before any row is
     *         created, so nothing is created
     */
    ImportedSupplies importSupplies(UUID communityId, byte[] csv);
}
