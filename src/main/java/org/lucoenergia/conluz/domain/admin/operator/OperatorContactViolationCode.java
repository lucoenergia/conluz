package org.lucoenergia.conluz.domain.admin.operator;

import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.lucoenergia.conluz.domain.shared.contact.PhoneNumberFormatChecker;

/**
 * Each way a configured operator contact can break CTC-005.
 */
public enum OperatorContactViolationCode {
    /** Something is configured, but not the name. */
    NAME_MISSING,
    /** Something is configured, but not the email address. */
    EMAIL_MISSING,
    /** The email address is not one by the rule of {@link ContactEmailValidator}. */
    EMAIL_INVALID,
    /** The phone number has something other than digits, spaces and one leading {@code +}. */
    PHONE_CHARACTERS_INVALID,
    /** The phone number has fewer than {@link PhoneNumberFormatChecker#MIN_DIGITS} digits. */
    PHONE_TOO_FEW_DIGITS
}
