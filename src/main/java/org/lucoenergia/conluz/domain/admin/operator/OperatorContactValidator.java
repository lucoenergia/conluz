package org.lucoenergia.conluz.domain.admin.operator;

import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.lucoenergia.conluz.domain.shared.contact.PhoneNumberFormatChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The rules a configured operator contact follows (CTC-005), applied to the configured values whatever they are read
 * from. It is the one place that knows them all.
 */
public final class OperatorContactValidator {

    private final ContactEmailValidator emailValidator;

    public OperatorContactValidator(ContactEmailValidator emailValidator) {
        this.emailValidator = emailValidator;
    }

    /**
     * Trims every value and treats one left blank as not configured, so a value made only of spaces is the same as
     * none. Then either nothing is configured, and there is no operator contact, or at least the name and the email
     * are, with an email address by {@link ContactEmailValidator} and a phone number, if any, by
     * {@link PhoneNumberFormatChecker}.
     *
     * @return the operator contact, or empty when none of its values is configured
     * @throws OperatorContactInvalidException with every violation found, if there is any
     */
    public Optional<OperatorContact> validate(OperatorContactDraft draft) {
        String name = blankToNull(draft.name());
        String email = blankToNull(draft.email());
        String phone = blankToNull(draft.phone());
        String schedule = blankToNull(draft.schedule());
        String notes = blankToNull(draft.notes());

        if (Stream.of(name, email, phone, schedule, notes).allMatch(value -> value == null)) {
            return Optional.empty();
        }
        List<OperatorContactViolationCode> violations = new ArrayList<>();
        if (name == null) {
            violations.add(OperatorContactViolationCode.NAME_MISSING);
        }
        if (email == null) {
            violations.add(OperatorContactViolationCode.EMAIL_MISSING);
        } else if (!emailValidator.isValid(email)) {
            violations.add(OperatorContactViolationCode.EMAIL_INVALID);
        }
        if (phone != null) {
            PhoneNumberFormatChecker.check(phone).ifPresent(violation -> violations.add(switch (violation) {
                case CHARACTERS_INVALID -> OperatorContactViolationCode.PHONE_CHARACTERS_INVALID;
                case TOO_FEW_DIGITS -> OperatorContactViolationCode.PHONE_TOO_FEW_DIGITS;
            }));
        }
        if (!violations.isEmpty()) {
            throw new OperatorContactInvalidException(violations);
        }
        return Optional.of(new OperatorContact(name, email, phone, schedule, notes));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
