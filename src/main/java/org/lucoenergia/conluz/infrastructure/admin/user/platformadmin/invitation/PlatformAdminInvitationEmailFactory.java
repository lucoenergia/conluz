package org.lucoenergia.conluz.infrastructure.admin.user.platformadmin.invitation;

import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailCategory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * The platform-admin invitation email: plain text, in Spanish. It carries the link and tells the person that they
 * will need the confirmation code from whoever invited them; it never carries the code (ADR-0004 §4). Its text is
 * the same whether or not the person already had an account.
 */
@Component
public class PlatformAdminInvitationEmailFactory {

    public static final EmailCategory CATEGORY = new EmailCategory("PLATFORM_ADMIN_INVITATION");

    static final Locale LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messageSource;

    public PlatformAdminInvitationEmailFactory(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Email build(String recipient, String link) {
        String subject = messageSource.getMessage("email.platform-admin-invitation.subject", null, LOCALE);
        String body = messageSource.getMessage("email.platform-admin-invitation.body", new Object[]{link}, LOCALE);
        return new Email(recipient, subject, body, CATEGORY);
    }
}
