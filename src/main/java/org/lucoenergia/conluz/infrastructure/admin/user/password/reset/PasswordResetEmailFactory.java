package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailCategory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Writes the password reset email: plain text, always in Spanish, whatever the language of the request.
 */
@Component
public class PasswordResetEmailFactory {

    public static final EmailCategory CATEGORY = new EmailCategory("PASSWORD_RESET");

    static final Locale LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messageSource;

    public PasswordResetEmailFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Email build(String recipient, String link) {
        String subject = messageSource.getMessage("email.password.reset.subject", null, LOCALE);
        String body = messageSource.getMessage("email.password.reset.body", new Object[]{link}, LOCALE);
        return new Email(recipient, subject, body, CATEGORY);
    }
}
