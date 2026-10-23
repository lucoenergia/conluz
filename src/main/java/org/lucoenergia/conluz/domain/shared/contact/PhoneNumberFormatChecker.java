package org.lucoenergia.conluz.domain.shared.contact;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a contact phone number looks like: digits and spaces with an optional leading {@code +}, and at least
 * {@link #MIN_DIGITS} digits. Declared once, so every contact that publishes a phone number is held to the same
 * definition of one.
 */
public final class PhoneNumberFormatChecker {

    public static final int MIN_DIGITS = 6;

    private static final Pattern CHARACTERS = Pattern.compile("\\+?[0-9 ]*");

    private PhoneNumberFormatChecker() {
    }

    /**
     * @param value a phone number, already trimmed
     * @return the first rule the value breaks, if any: its characters are checked before its digits are counted
     */
    public static Optional<Violation> check(String value) {
        if (!CHARACTERS.matcher(value).matches()) {
            return Optional.of(Violation.CHARACTERS_INVALID);
        }
        long digits = value.chars().filter(Character::isDigit).count();
        if (digits < MIN_DIGITS) {
            return Optional.of(Violation.TOO_FEW_DIGITS);
        }
        return Optional.empty();
    }

    public enum Violation {
        /** Something other than digits, spaces and one leading {@code +}. */
        CHARACTERS_INVALID,
        /** Fewer than {@link #MIN_DIGITS} digits. */
        TOO_FEW_DIGITS
    }
}
