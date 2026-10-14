package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailCategory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The invitation email: plain text, in Spanish, naming the community. Its text is the same whether or not the
 * person already had an account, so it reveals nothing about one.
 */
@Component
public class InvitationEmailFactory {

    public static final EmailCategory CATEGORY = new EmailCategory("INVITATION");

    static final Locale LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messageSource;

    public InvitationEmailFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Email build(String recipient, String communityName, String link) {
        String subject = messageSource.getMessage("email.invitation.subject", new Object[]{communityName}, LOCALE);
        String body = messageSource.getMessage("email.invitation.body", new Object[]{communityName, link}, LOCALE);
        return new Email(recipient, subject, body, CATEGORY);
    }
}
