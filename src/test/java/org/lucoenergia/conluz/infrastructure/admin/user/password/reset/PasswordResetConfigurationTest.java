package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PasswordResetConfiguration.class);

    @ParameterizedTest
    @CsvSource({
            "true, '', true",
            "true, not a url, true",
            "true, https://app.example.org, false",
            "false, '', false",
            "false, https://app.example.org, false"
    })
    void warnsOnceAtStartup_onlyWhenEmailsAreSentButThereIsNothingToLinkTo(boolean mailEnabled, String publicUrl,
                                                                            boolean warns) {
        try (LogCapture logs = LogCapture.of(PasswordResetConfiguration.class)) {
            runner.withPropertyValues("conluz.mail.enabled=" + mailEnabled, "conluz.web.public-url=" + publicUrl)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(PasswordResetLink.class).isConfigured())
                                .isEqualTo(!publicUrl.isBlank() && !publicUrl.contains(" "));
                    });

            List<ILoggingEvent> warnings = logs.warningsAndAbove();
            if (warns) {
                assertThat(warnings).hasSize(1);
                assertThat(warnings.get(0).getLevel()).isEqualTo(Level.WARN);
                assertThat(warnings.get(0).getFormattedMessage()).isEqualTo("Email sending is enabled but "
                        + "CONLUZ_PUBLIC_WEB_URL is missing or invalid; no password reset email will be sent");
            } else {
                assertThat(warnings).isEmpty();
            }
        }
    }
}
