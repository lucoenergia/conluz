package org.lucoenergia.conluz.infrastructure.admin.community.contact;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.lucoenergia.conluz.domain.admin.community.contact.ContactEmailValidity;
import org.springframework.stereotype.Component;

/**
 * Runs the {@link Email} constraint that the member contact emails are validated with, so the verdict is that rule's
 * by construction rather than a second definition of an email address.
 */
@Component
public class ContactEmailValidityImpl implements ContactEmailValidity {

    private final Validator validator;

    public ContactEmailValidityImpl(Validator validator) {
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
