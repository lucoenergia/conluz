package org.lucoenergia.conluz.domain.shared.email;

import java.util.Objects;

/**
 * A plain-text email to one recipient.
 *
 * <p>The body may carry one-time tokens and the recipient is personal data, so neither them nor the subject may
 * ever be logged. {@link #toString()} prints the category only.</p>
 *
 * @param recipient the address the email is sent to
 * @param subject   the subject line
 * @param body      the plain-text body
 * @param category  the non-sensitive label the email is logged under
 */
public record Email(String recipient, String subject, String body, EmailCategory category) {

    public Email {
        requireText(recipient, "recipient");
        requireText(subject, "subject");
        requireText(body, "body");
        Objects.requireNonNull(category, "category");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The email " + name + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "Email{category=" + category + '}';
    }
}
