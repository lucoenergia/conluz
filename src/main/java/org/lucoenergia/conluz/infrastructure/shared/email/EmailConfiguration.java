package org.lucoenergia.conluz.infrastructure.shared.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Chooses how emails are delivered, from the {@code conluz.mail.*} settings.
 *
 * <p>The {@link JavaMailSenderImpl} is built here and kept out of the context on purpose: as a bean it would
 * register Spring Boot's mail health indicator, and an unreachable SMTP server would then mark the whole
 * application down.</p>
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailConfiguration.class);

    /**
     * Applies to connecting, reading and writing alike, so that a server that hangs holds a sending thread for a
     * bounded time.
     */
    static final int TIMEOUT_MILLIS = 5000;

    @Bean
    EmailTransport emailTransport(EmailProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledEmailTransport();
        }
        List<String> invalid = invalidSettings(properties);
        if (!invalid.isEmpty()) {
            LOGGER.warn("Email sending is enabled but not configured (missing or invalid: {}); every email will fail",
                    String.join(", ", invalid));
            return new UnconfiguredEmailTransport();
        }
        return new SmtpEmailTransport(mailSender(properties), properties.getFromAddress(), properties.getFromName());
    }

    /**
     * The names of the settings that are missing or invalid. Never their values.
     */
    static List<String> invalidSettings(EmailProperties properties) {
        List<String> invalid = new ArrayList<>();
        if (isBlank(properties.getHost())) {
            invalid.add("host");
        }
        if (parsePort(properties.getPort()) == null) {
            invalid.add("port");
        }
        if (isBlank(properties.getFromAddress())) {
            invalid.add("from-address");
        }
        if (!isBlank(properties.getUsername()) && isBlank(properties.getPassword())) {
            invalid.add("password");
        }
        return invalid;
    }

    /**
     * Only called with settings that {@link #invalidSettings} accepts.
     */
    static JavaMailSenderImpl mailSender(EmailProperties properties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.getHost().trim());
        mailSender.setPort(parsePort(properties.getPort()));
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        boolean authenticated = !isBlank(properties.getUsername());
        if (authenticated) {
            mailSender.setUsername(properties.getUsername());
            mailSender.setPassword(properties.getPassword());
        }

        Properties javaMail = mailSender.getJavaMailProperties();
        javaMail.setProperty("mail.transport.protocol", "smtp");
        javaMail.setProperty("mail.smtp.auth", String.valueOf(authenticated));
        javaMail.setProperty("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
        javaMail.setProperty("mail.smtp.timeout", String.valueOf(TIMEOUT_MILLIS));
        javaMail.setProperty("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MILLIS));
        // Required, not only enabled: a server that does not offer STARTTLS is refused rather than sent the
        // credentials in clear.
        javaMail.setProperty("mail.smtp.starttls.enable", String.valueOf(properties.isStarttls()));
        javaMail.setProperty("mail.smtp.starttls.required", String.valueOf(properties.isStarttls()));
        // Its default differs between JavaMail versions. Without it, anyone presenting any valid certificate
        // during STARTTLS would receive the credentials.
        javaMail.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        // The debug output prints the whole conversation, credentials and content included.
        javaMail.setProperty("mail.debug", "false");
        return mailSender;
    }

    private static Integer parsePort(String port) {
        if (isBlank(port)) {
            return null;
        }
        try {
            int value = Integer.parseInt(port.trim());
            return value >= 1 && value <= 65535 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
