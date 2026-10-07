package org.lucoenergia.conluz.infrastructure.shared.email;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lucoenergia.conluz.infrastructure.shared.email.EmailTestData.*;

/**
 * The application's own sender, switched on through its settings, against an embedded SMTP server that requires
 * authentication.
 */
class EmailSendingEnabledTest extends BaseIntegrationTest {

    private static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort())
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(USERNAME, USERNAME, PASSWORD));

    static {
        GREEN_MAIL.start();
    }

    @Autowired
    private EmailSender emailSender;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("conluz.mail.enabled", () -> "true");
        registry.add("conluz.mail.host", () -> "127.0.0.1");
        registry.add("conluz.mail.port", () -> String.valueOf(GREEN_MAIL.getSmtp().getPort()));
        registry.add("conluz.mail.username", () -> USERNAME);
        registry.add("conluz.mail.password", () -> PASSWORD);
        registry.add("conluz.mail.from-address", () -> FROM_ADDRESS);
        registry.add("conluz.mail.from-name", () -> FROM_NAME);
        // The embedded server speaks plain SMTP
        registry.add("conluz.mail.starttls", () -> "false");
    }

    @AfterAll
    static void stopServer() {
        GREEN_MAIL.stop();
    }

    @Test
    void sendsAPlainTextUtf8EmailFromTheConfiguredSender() throws Exception {
        try (LogCapture logs = LogCapture.ofRoot()) {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> emailSender.send(email("ENABLED")));

            assertThat(GREEN_MAIL.waitForIncomingEmail(10_000, 1)).isTrue();
            MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
            assertThat(received).hasSize(1);
            MimeMessage message = received[0];

            InternetAddress from = (InternetAddress) message.getFrom()[0];
            assertThat(from.getAddress()).isEqualTo(FROM_ADDRESS);
            assertThat(from.getPersonal()).isEqualTo(FROM_NAME);
            assertThat(message.getRecipients(Message.RecipientType.TO))
                    .extracting(address -> ((InternetAddress) address).getAddress())
                    .containsExactly(RECIPIENT);
            assertThat(message.getSubject()).isEqualTo(SUBJECT);
            // Encoded on the wire, not sent as raw 8-bit headers
            assertThat(message.getHeader("Subject")[0]).startsWithIgnoringCase("=?UTF-8?");
            assertThat(message.getHeader("From")[0]).containsIgnoringCase("=?UTF-8?");
            assertThat(message.getContentType()).startsWith("text/plain").containsIgnoringCase("charset=UTF-8");
            // SMTP carries line breaks as CRLF
            assertThat(((String) message.getContent()).replace("\r\n", "\n")).isEqualTo(BODY);

            assertThat(logs.warningsAndAbove()).isEmpty();
            assertNoSensitiveData(logs.all());
        }
    }
}
