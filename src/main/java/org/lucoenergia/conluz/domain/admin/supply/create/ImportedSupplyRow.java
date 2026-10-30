package org.lucoenergia.conluz.domain.admin.supply.create;

import java.util.List;
import java.util.Map;

/**
 * The outcome of one data row of an imported file of supplies.
 *
 * @param line       the line of the file the row starts on, the header being line 1
 * @param violations why the row is {@link SupplyImportRowOutcome#INVALID}, one per rule broken; empty otherwise
 */
public record ImportedSupplyRow(int line, SupplyImportRowOutcome outcome, List<ColumnViolation> violations) {

    public ImportedSupplyRow {
        violations = List.copyOf(violations);
    }

    public static ImportedSupplyRow of(int line, SupplyImportRowOutcome outcome) {
        return new ImportedSupplyRow(line, outcome, List.of());
    }

    public boolean isCreated() {
        return outcome == SupplyImportRowOutcome.CREATED;
    }

    /**
     * A rule that a column's value breaks. Names the column and the rule, never the value.
     *
     * @param params the rule's parameters, formatted without a locale; empty if it has none
     */
    public record ColumnViolation(String column, SupplyImportConstraint constraint, Map<String, String> params) {

        public ColumnViolation {
            params = Map.copyOf(params);
        }

        public static ColumnViolation of(String column, SupplyImportConstraint constraint) {
            return new ColumnViolation(column, constraint, Map.of());
        }
    }
}
