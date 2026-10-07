package org.lucoenergia.conluz.infrastructure.shared.email;

import jakarta.mail.internet.MimeMessage;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

/**
 * Delivers an email through an SMTP server, as plain text in UTF-8.
 */
class SmtpEmailTransport implements EmailTransport {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    SmtpEmailTransport(JavaMailSender mailSender, String fromAddress, String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void send(Email email) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        if (fromName == null || fromName.isBlank()) {
            helper.setFrom(fromAddress);
        } else {
            helper.setFrom(fromAddress, fromName);
        }
        helper.setTo(email.recipient());
        helper.setSubject(email.subject());
        helper.setText(email.body(), false);
        mailSender.send(message);
    }
}
