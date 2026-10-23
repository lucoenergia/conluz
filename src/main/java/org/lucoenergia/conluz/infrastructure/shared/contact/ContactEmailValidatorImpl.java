package org.lucoenergia.conluz.infrastructure.shared.contact;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.lucoenergia.conluz.domain.shared.contact.ContactEmailValidator;
import org.springframework.stereotype.Component;

/**
 * Runs the {@link Email} constraint that the member contact emails are validated with, so the verdict is that rule's
 * by construction rather than a second definition of an email address.
 */
@Component
public class ContactEmailValidatorImpl implements ContactEmailValidator {

    private final Validator validator;

    public ContactEmailValidatorImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public boolean isValid(String email) {
        return validator.validateValue(EmailAddress.class, "value", email).isEmpty();
    }

    /**
     * Only carries the constraint for {@link Validator#validateValue}; never instantiated.
     */
    private static final class EmailAddress {

        @Email
        private String value;
    }
}
