package org.lucoenergia.conluz.domain.shared;

import java.util.regex.Pattern;

/**
 * Tells whether a normalised personal id (see {@link UserPersonalId#normalize(String)}) is a Spanish identifier
 * whose control character matches its digits (#398). Four forms are accepted:
 * <ul>
 *     <li>DNI: 8 digits and a control letter, the number modulo 23 over {@value #DNI_LETTERS};</li>
 *     <li>NIE: {@code X}, {@code Y} or {@code Z}, 7 digits and a control letter, the prefix standing for 0, 1 or 2
 *     and the letter computed as a DNI's;</li>
 *     <li>NIF of a natural person with {@code K}, {@code L} or {@code M}: the letter, 7 digits and a control letter,
 *     computed on the 7 digits as a DNI's;</li>
 *     <li>CIF: an organisation letter, 7 digits and a control character, which is a digit for {@code A}, {@code B},
 *     {@code E} and {@code H}, a letter for {@code N}, {@code P}, {@code Q}, {@code R}, {@code S} and {@code W},
 *     and either for {@code C}, {@code D}, {@code F}, {@code G}, {@code J}, {@code U} and {@code V}.</li>
 * </ul>
 *
 * <p>It answers only whether the identifier is valid, never which rule it breaks, so that every refusal is the
 * same.</p>
 */
public final class UserPersonalIdValidator {

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String CIF_LETTERS = "JABCDEFGHI";

    private static final Pattern DNI = Pattern.compile("[0-9]{8}[A-Z]");
    private static final Pattern NIE = Pattern.compile("[XYZ][0-9]{7}[A-Z]");
    private static final Pattern PERSONAL_NIF = Pattern.compile("[KLM][0-9]{7}[A-Z]");
    private static final Pattern CIF = Pattern.compile("[ABCDEFGHJNPQRSUVW][0-9]{7}[0-9A-J]");

    private static final String CIF_DIGIT_CONTROL = "ABEH";
    private static final String CIF_LETTER_CONTROL = "NPQRSW";

    private UserPersonalIdValidator() {
    }

    /**
     * @param normalized a personal id already normalised; {@code null} is not valid
     */
    public static boolean isValid(String normalized) {
        if (normalized == null) {
            return false;
        }
        if (DNI.matcher(normalized).matches()) {
            return hasDniLetter(normalized.substring(0, 8), normalized.charAt(8));
        }
        if (NIE.matcher(normalized).matches()) {
            String number = "XYZ".indexOf(normalized.charAt(0)) + normalized.substring(1, 8);
            return hasDniLetter(number, normalized.charAt(8));
        }
        if (PERSONAL_NIF.matcher(normalized).matches()) {
            return hasDniLetter(normalized.substring(1, 8), normalized.charAt(8));
        }
        if (CIF.matcher(normalized).matches()) {
            return hasCifControl(normalized.charAt(0), normalized.substring(1, 8), normalized.charAt(8));
        }
        return false;
    }

    private static boolean hasDniLetter(String digits, char letter) {
        return DNI_LETTERS.charAt(Integer.parseInt(digits) % 23) == letter;
    }

    private static boolean hasCifControl(char organisation, String digits, char control) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(i) - '0';
            if (i % 2 == 0) {
                // Positions 1, 3, 5 and 7: the digit doubled, and the digits of the result added up
                int doubled = digit * 2;
                sum += doubled / 10 + doubled % 10;
            } else {
                sum += digit;
            }
        }
        int controlDigit = (10 - sum % 10) % 10;
        boolean isDigit = control == (char) ('0' + controlDigit);
        boolean isLetter = control == CIF_LETTERS.charAt(controlDigit);
        if (CIF_DIGIT_CONTROL.indexOf(organisation) >= 0) {
            return isDigit;
        }
        if (CIF_LETTER_CONTROL.indexOf(organisation) >= 0) {
            return isLetter;
        }
        return isDigit || isLetter;
    }
}
