package org.lucoenergia.conluz.domain.admin.operator;

import java.util.List;

/**
 * A configured operator contact breaks CTC-005. Carries every violation found, so the configuration can be fixed all
 * at once. Its message names the violations only, never the configured values.
 */
public class OperatorContactInvalidException extends RuntimeException {

    private final transient List<OperatorContactViolationCode> violations;

    public OperatorContactInvalidException(List<OperatorContactViolationCode> violations) {
        super("The operator contact breaks " + violations.size() + " rule(s): " + violations);
        this.violations = List.copyOf(violations);
    }

    public List<OperatorContactViolationCode> getViolations() {
        return violations;
    }
}
