package org.lucoenergia.conluz.domain.admin.community.membership.register;

import java.util.List;
import java.util.Map;

/**
 * The outcome of one data row of an imported file.
 *
 * @param line          the line of the file the row starts on, the header being line 1
 * @param violations    why the row is {@link MemberImportRowOutcome#INVALID}, one per rule broken, or
 *                      {@link MemberImportRowOutcome#INVALID_PERSONAL_ID}; empty otherwise
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
     * A rule of the registration that a column's value breaks. Names the column and the rule, never the value.
     *
     * @param params the rule's parameters, such as {@code min}, formatted without a locale; empty if it has none
     */
    public record ColumnViolation(String column, MemberImportConstraint constraint, Map<String, String> params) {

        public ColumnViolation {
            params = Map.copyOf(params);
        }

        public static ColumnViolation of(String column, MemberImportConstraint constraint) {
            return new ColumnViolation(column, constraint, Map.of());
        }
    }
}
