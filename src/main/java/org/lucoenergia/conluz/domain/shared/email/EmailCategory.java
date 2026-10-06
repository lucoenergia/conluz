package org.lucoenergia.conluz.domain.shared.email;

import java.util.regex.Pattern;

/**
 * The kind of an email, such as {@code PASSWORD_RESET}: the only thing about an email that is ever logged.
 *
 * <p>It is restricted to upper-case letters, digits and underscores, so that no address, name or token can be
 * passed off as one.</p>
 *
 * @param value the label
 */
public record EmailCategory(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    public EmailCategory {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "An email category must be 1 to 40 upper-case letters, digits or underscores, starting with a letter");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
