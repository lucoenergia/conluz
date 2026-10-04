package org.lucoenergia.conluz.domain.admin.user.password;

/**
 * The rule of the {@link PasswordPolicy} that a rejected password failed.
 */
public enum PasswordPolicyViolation {
    TOO_SHORT,
    TOO_LONG,
    TOO_MANY_BYTES
}
