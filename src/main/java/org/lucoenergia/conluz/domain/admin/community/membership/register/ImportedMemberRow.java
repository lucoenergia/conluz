package org.lucoenergia.conluz.domain.admin.community.membership.register;

import java.util.List;

/**
 * The outcome of one data row of an imported file.
 *
 * @param line          the line of the file the row starts on, the header being line 1
 * @param violations    why the row is {@link MemberImportRowOutcome#INVALID}, one per invalid column; empty otherwise
 */
public record ImportedMemberRow(int line, MemberImportRowOutcome outcome, List<ColumnViolation> violations) {

    public ImportedMemberRow {
        violations = List.copyOf(violations);
    }

    public static ImportedMemberRow of(int line, MemberImportRowOutcome outcome) {
        return new ImportedMemberRow(line, outcome, List.of());
    }

    public boolean isRegistered() {
        return outcome == MemberImportRowOutcome.REGISTERED;
    }

    /**
     * A column whose value breaks a rule of the registration. Names the column and the rule, never the value.
     */
    public record ColumnViolation(String column, String reason) {
    }
}
