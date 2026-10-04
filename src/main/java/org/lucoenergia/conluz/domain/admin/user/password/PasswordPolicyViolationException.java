package org.lucoenergia.conluz.domain.admin.user.password;

/**
 * A password was rejected by the {@link PasswordPolicy}. The message names only the rule that failed, never
 * the password itself, so the exception is safe to log.
 */
public class PasswordPolicyViolationException extends RuntimeException {

    private final PasswordPolicyViolation rule;

    public PasswordPolicyViolationException(PasswordPolicyViolation rule) {
        super("Password rejected by the password policy: " + rule);
        this.rule = rule;
    }

    public PasswordPolicyViolation getRule() {
        return rule;
    }
}
