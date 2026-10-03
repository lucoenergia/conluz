package org.lucoenergia.conluz.domain.admin.user.password;

import java.nio.charset.StandardCharsets;

/**
 * The single password policy, applied to every password that is set: on user creation, on each row of a user
 * import, on the initial admin created by {@code /init}, and on a password change.
 * <p>
 * It follows NIST SP 800-63B-4 for passwords used as the single authentication factor: a length rule and
 * nothing else. Every character is accepted, spaces and non-ASCII letters included, and there are no
 * composition rules. The value is checked exactly as received: it is never trimmed, normalised or otherwise
 * transformed, so the password that is hashed is the password the user typed.
 * <p>
 * Length is counted in Unicode code points, so a character outside the Basic Multilingual Plane counts as one.
 * The byte limit exists because BCrypt only reads the first 72 bytes of its input: rejecting anything longer
 * guarantees that no part of an accepted password is silently ignored.
 */
public final class PasswordPolicy {

    public static final int MIN_CODE_POINTS = 15;
    public static final int MAX_CODE_POINTS = 64;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    /**
     * Checks a password against the policy, reporting the first rule that fails, in this order: too short, too
     * long, too many bytes. A {@code null} password is treated as empty, and so as too short.
     *
     * @throws PasswordPolicyViolationException if the password does not satisfy the policy
     */
    public static void check(String password) {
        if (password == null) {
            throw new PasswordPolicyViolationException(PasswordPolicyRule.TOO_SHORT);
        }
        int codePoints = password.codePointCount(0, password.length());
        if (codePoints < MIN_CODE_POINTS) {
            throw new PasswordPolicyViolationException(PasswordPolicyRule.TOO_SHORT);
        }
        if (codePoints > MAX_CODE_POINTS) {
            throw new PasswordPolicyViolationException(PasswordPolicyRule.TOO_LONG);
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new PasswordPolicyViolationException(PasswordPolicyRule.TOO_MANY_BYTES);
        }
    }
}
