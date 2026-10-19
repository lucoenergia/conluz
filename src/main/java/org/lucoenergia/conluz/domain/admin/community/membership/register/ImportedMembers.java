package org.lucoenergia.conluz.domain.admin.community.membership.register;

import java.util.List;

/**
 * The outcome of importing a file of members: one row per data row of the file, in its order.
 */
public record ImportedMembers(List<ImportedMemberRow> rows) {

    public ImportedMembers {
        rows = List.copyOf(rows);
    }

    public int registered() {
        return (int) rows.stream().filter(ImportedMemberRow::isRegistered).count();
    }

    public int failed() {
        return rows.size() - registered();
    }
}
