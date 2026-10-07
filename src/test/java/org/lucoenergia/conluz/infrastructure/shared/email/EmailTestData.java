package org.lucoenergia.conluz.infrastructure.shared.email;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.lucoenergia.conluz.domain.shared.email.Email;
import org.lucoenergia.conluz.domain.shared.email.EmailCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The values the email tests send, chosen so that any of them showing up in a log line is unmistakable, and the
 * check that none of them does.
 */
final class EmailTestData {

    static final String RECIPIENT = "destinataria.secreta@example.org";
    static final String SUBJECT = "Recuperación de contraseña – asunto secreto";
    static final String BODY = "Hola, María José:\n\nTu código de un solo uso es ÑANDÚ-7f3e9c-secreto.\n\nUn saludo.";
    static final String USERNAME = "remitente@example.org";
    static final String PASSWORD = "contraseña-smtp-secreta-42";
    static final String FROM_ADDRESS = "comunidad@example.org";
    static final String FROM_NAME = "Comunidad Energética Ñandú";

    private EmailTestData() {
    }

    static Email email(String category) {
        return email(category, SUBJECT);
    }

    static Email email(String category, String subject) {
        return new Email(RECIPIENT, subject, BODY, new EmailCategory(category));
    }

    static EmailProperties enabledProperties(String host, int port) {
        return new EmailProperties(true, host, String.valueOf(port), USERNAME, PASSWORD, FROM_ADDRESS, FROM_NAME,
                false);
    }

    static EmailProperties disabledProperties(String host, int port) {
        return new EmailProperties(false, host, String.valueOf(port), USERNAME, PASSWORD, FROM_ADDRESS, FROM_NAME,
                false);
    }

    /**
     * The embedded SMTP server stands in for the remote one, and logs what it receives as a real server would on
     * its own host. Its lines are not the application's.
     */
    private static final String EMBEDDED_SERVER_LOGGERS = "com.icegreen.greenmail";

    /**
     * No message, argument or exception of any event the application logs carries the recipient, the subject,
     * the body or the password, whole or in part.
     */
    static void assertNoSensitiveData(List<ILoggingEvent> events, String... extraSubjects) {
        List<String> secrets = new ArrayList<>(List.of(RECIPIENT, "destinataria.secreta", SUBJECT, "asunto secreto",
                BODY, "ÑANDÚ-7f3e9c", PASSWORD));
        secrets.addAll(Arrays.asList(extraSubjects));
        for (ILoggingEvent event : events) {
            if (event.getLoggerName().startsWith(EMBEDDED_SERVER_LOGGERS)) {
                continue;
            }
            List<String> texts = new ArrayList<>();
            texts.add(event.getFormattedMessage());
            texts.add(event.getMessage());
            if (event.getArgumentArray() != null) {
                Arrays.stream(event.getArgumentArray()).map(String::valueOf).forEach(texts::add);
            }
            for (IThrowableProxy proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
                texts.add(proxy.getMessage());
            }
            for (String text : texts) {
                if (text == null) {
                    continue;
                }
                for (String secret : secrets) {
                    assertThat(text)
                            .as("a line logged by %s", event.getLoggerName())
                            .doesNotContain(secret);
                }
            }
        }
    }
}
