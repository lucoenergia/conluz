package org.lucoenergia.conluz.domain.shared.email;

import java.util.regex.Pattern;

/**
 * How "no email address" is recorded: as {@code null}, never as an empty or blank string (#389).
 */
public final class EmailAddresses {

    /**
     * A value made only of space, tab, line feed, carriage return, form feed and vertical tab, or empty. The set
     * is explicit rather than {@link String#isBlank()} so that the Liquibase changeset
     * {@code normalize_users_blank_email_to_null} treats exactly the same values as blank in SQL.
     */
    private static final Pattern BLANK = Pattern.compile("[ \\t\\n\\r\\f\\x0B]*");

    private EmailAddresses() {
    }

    /**
     * The email address as given, or {@code null} if it is {@code null}, empty or blank.
     */
    public static String blankToNull(String email) {
        if (email == null || BLANK.matcher(email).matches()) {
            return null;
        }
        return email;
    }
}
