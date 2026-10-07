package org.lucoenergia.conluz.infrastructure.shared.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.lucoenergia.conluz.infrastructure.shared.email.EmailTestData.*;

class EmailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfiguration.class, AfterCommitEmailSender.class);

    @Test
    void sendingIsDisabledWhenNotSwitchedOn() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EmailTransport.class)).isInstanceOf(DisabledEmailTransport.class);
        });
    }

    @Test
    void neitherAnExecutorNorAMailSenderBecomesABean() {
        contextRunner.withPropertyValues(completeSettings()).run(context -> {
            assertThat(context.getBean(EmailTransport.class)).isInstanceOf(SmtpEmailTransport.class);
            assertThat(context).doesNotHaveBean(Executor.class);
            assertThat(context).doesNotHaveBean(JavaMailSender.class);
        });
    }

    @Test
    void startsAndWarnsOnceWhenSwitchedOnWithoutAHost() {
        try (LogCapture logs = LogCapture.ofRoot()) {
            contextRunner
                    .withPropertyValues(
                            "conluz.mail.enabled=true",
                            "conluz.mail.username=" + USERNAME,
                            "conluz.mail.password=" + PASSWORD,
                            "conluz.mail.from-address=" + FROM_ADDRESS)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(EmailTransport.class))
                                .isInstanceOf(UnconfiguredEmailTransport.class);
                        assertThat(logs.warningsAndAbove())
                                .singleElement()
                                .satisfies(event -> {
                                    assertThat(event.getFormattedMessage()).isEqualTo(
                                            "Email sending is enabled but not configured (missing or invalid: host);"
                                                    + " every email will fail");
                                    assertThat(event.getThrowableProxy()).isNull();
                                });

                        // Every email then fails as any other failure does
                        logs.clear();
                        context.getBean(EmailSender.class).send(email("UNCONFIGURED"));
                        await().atMost(Duration.ofSeconds(10)).until(() -> !logs.warningsAndAbove().isEmpty());
                        assertThat(logs.warningsAndAbove())
                                .extracting(ILoggingEvent::getFormattedMessage)
                                .containsExactly("Email UNCONFIGURED not sent: EmailNotConfiguredException");
                    });
            assertNoSensitiveData(logs.all(), USERNAME, FROM_ADDRESS);
        }
    }

    @Test
    void namesEveryInvalidSettingButNoValue() {
        try (LogCapture logs = LogCapture.of(EmailConfiguration.class)) {
            contextRunner
                    .withPropertyValues(
                            "conluz.mail.enabled=true",
                            "conluz.mail.host= ",
                            "conluz.mail.port=not-a-port",
                            "conluz.mail.username=" + USERNAME)
                    .run(context -> assertThat(context).hasNotFailed());

            List<ILoggingEvent> warnings = logs.warningsAndAbove();
            assertThat(warnings)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("Email sending is enabled but not configured"
                            + " (missing or invalid: host, port, from-address, password); every email will fail");
            assertThat(warnings.get(0).getLevel()).isEqualTo(Level.WARN);
        }
    }

    @Test
    void acceptsCompleteSettingsWithoutWarning() {
        try (LogCapture logs = LogCapture.of(EmailConfiguration.class)) {
            contextRunner.withPropertyValues(completeSettings()).run(context -> assertThat(context).hasNotFailed());

            assertThat(logs.warningsAndAbove()).isEmpty();
        }
    }

    @Test
    void boundsEveryWaitAndHardensTheConnection() {
        JavaMailSenderImpl mailSender = EmailConfiguration.mailSender(new EmailProperties(true, "smtp.example.org",
                "587", USERNAME, PASSWORD, FROM_ADDRESS, FROM_NAME, true));

        Properties properties = mailSender.getJavaMailProperties();
        assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
        assertThat(properties.getProperty("mail.smtp.timeout")).isEqualTo("5000");
        assertThat(properties.getProperty("mail.smtp.writetimeout")).isEqualTo("5000");
        assertThat(properties.getProperty("mail.debug")).isEqualTo("false");
        assertThat(properties.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("true");
        assertThat(mailSender.getSession().getDebug()).isFalse();
        assertThat(mailSender.getHost()).isEqualTo("smtp.example.org");
        assertThat(mailSender.getPort()).isEqualTo(587);
        assertThat(mailSender.getDefaultEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void doesNotAuthenticateWithoutAUsername() {
        JavaMailSenderImpl mailSender = EmailConfiguration.mailSender(new EmailProperties(true, "smtp.example.org",
                "25", null, null, FROM_ADDRESS, null, false));

        Properties properties = mailSender.getJavaMailProperties();
        assertThat(properties.getProperty("mail.smtp.auth")).isEqualTo("false");
        assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo("false");
        assertThat(mailSender.getUsername()).isNull();
    }

    @Test
    void neverPrintsThePasswordOfItsSettings() {
        EmailProperties properties = new EmailProperties(true, "smtp.example.org", "587", USERNAME, PASSWORD,
                FROM_ADDRESS, FROM_NAME, true);

        assertThat(properties.toString()).doesNotContain(PASSWORD).doesNotContain(USERNAME);
    }

    private static String[] completeSettings() {
        return new String[]{
                "conluz.mail.enabled=true",
                "conluz.mail.host=smtp.example.org",
                "conluz.mail.port=587",
                "conluz.mail.username=" + USERNAME,
                "conluz.mail.password=" + PASSWORD,
                "conluz.mail.from-address=" + FROM_ADDRESS,
                "conluz.mail.from-name=" + FROM_NAME
        };
    }
}
