package org.lucoenergia.conluz.infrastructure.admin.operator;

import org.lucoenergia.conluz.domain.admin.operator.OperatorContact;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.lucoenergia.conluz.domain.shared.contact.PhoneNumberFormatChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.lucoenergia.conluz.infrastructure.admin.operator.OperatorContactProperties.EMAIL_VARIABLE;
import static org.lucoenergia.conluz.infrastructure.admin.operator.OperatorContactProperties.NAME_VARIABLE;
import static org.lucoenergia.conluz.infrastructure.admin.operator.OperatorContactProperties.PHONE_VARIABLE;

/**
 * Turns the operator contact settings into an {@link OperatorContact}, or refuses them (CTC-005).
 *
 * <p>Every value is trimmed first, and one left blank counts as not set, so a variable declared empty in an
 * {@code .env} file is the same as one left out. Then either nothing is set, and there is no operator contact, or at
 * least the name and the email are, with an email address by the rule {@link ContactEmailValidator} applies and a
 * phone number, if any, by {@link PhoneNumberFormatChecker}. Every problem is reported at once, by the environment variable
 * that causes it; values are never repeated in the message.</p>
 */
public class OperatorContactPropertiesValidator {

    private final ContactEmailValidator emailValidity;

    public OperatorContactPropertiesValidator(ContactEmailValidator emailValidity) {
        this.emailValidity = emailValidity;
    }

    /**
     * @return the operator contact, or empty when none of its settings is set
     * @throws IllegalStateException naming every variable to fix, if the settings are partial or invalid
     */
    public Optional<OperatorContact> validate(OperatorContactProperties properties) {
        String name = blankToNull(properties.name());
        String email = blankToNull(properties.email());
        String phone = blankToNull(properties.phone());
        String schedule = blankToNull(properties.schedule());
        String notes = blankToNull(properties.notes());

        if (Stream.of(name, email, phone, schedule, notes).allMatch(value -> value == null)) {
            return Optional.empty();
        }
        List<String> problems = new ArrayList<>();
        if (name == null) {
            problems.add(NAME_VARIABLE + " is not set");
        }
        if (email == null) {
            problems.add(EMAIL_VARIABLE + " is not set");
        } else if (!emailValidity.isValid(email)) {
            problems.add(EMAIL_VARIABLE + " is not an email address");
        }
        if (phone != null) {
            PhoneNumberFormatChecker.check(phone).ifPresent(violation -> problems.add(switch (violation) {
                case CHARACTERS_INVALID -> PHONE_VARIABLE + " may only have digits, spaces and a leading +";
                case TOO_FEW_DIGITS -> PHONE_VARIABLE + " must have at least " + PhoneNumberFormatChecker.MIN_DIGITS
                        + " digits";
            }));
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The operator contact is misconfigured: " + String.join("; ", problems)
                    + ". Set at least " + NAME_VARIABLE + " and " + EMAIL_VARIABLE
                    + ", or none of the CONLUZ_OPERATOR_CONTACT_* variables.");
        }
        return Optional.of(new OperatorContact(name, email, phone, schedule, notes));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
