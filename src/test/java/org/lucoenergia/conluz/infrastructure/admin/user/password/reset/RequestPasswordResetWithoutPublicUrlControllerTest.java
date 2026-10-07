package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.email.EmailSender;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Email sending is on, but the public web URL is not configured: there is nothing to link to (#362). The warning
 * logged once at startup is covered by {@link PasswordResetConfigurationTest}.
 */
class RequestPasswordResetWithoutPublicUrlControllerTest extends BaseControllerTest {

    @MockitoSpyBean
    private EmailSender emailSender;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @DynamicPropertySource
    static void mailOnWithoutPublicUrl(DynamicPropertyRegistry registry) {
        registry.add("conluz.mail.enabled", () -> "true");
        // Never contacted: no email is ever handed over
        registry.add("conluz.mail.host", () -> "127.0.0.1");
        registry.add("conluz.mail.from-address", () -> "no-reply@conluz.test");
        registry.add("conluz.web.public-url", () -> "");
    }

    @AfterEach
    void deleteTheUser() {
        if (user != null) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());
        }
    }

    @Test
    void anEligibleUser_isAnswered202_butNoTokenIsIssuedAndNoEmailSent_withOneWarning() throws Exception {
        user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);

        try (LogCapture logs = LogCapture.ofRoot()) {
            MvcResult result = mockMvc.perform(post("/api/v1/users/password/recover")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("personalId", user.getPersonalId()))))
                    .andExpect(status().isAccepted())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).isEmpty();
            verify(emailSender, never()).send(any());
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM one_time_token WHERE user_id = ?",
                    Integer.class, user.getId())).isZero();

            List<ILoggingEvent> warnings = logs.warningsAndAboveOfThisThread();
            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(warnings.get(0).getFormattedMessage())
                    .isEqualTo("Email PASSWORD_RESET not sent: the public web URL is not configured");
            assertThat(logs.allOfThisThread())
                    .filteredOn(event -> event.getLoggerName().equals(RequestPasswordResetServiceImpl.class.getName())
                            && event.getLevel() == Level.INFO)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getFormattedMessage()).endsWith("outcome=FAILED"));
            for (ILoggingEvent event : logs.all()) {
                assertThat(event.getFormattedMessage())
                        .doesNotContain(user.getEmail())
                        .doesNotContain(user.getPassword());
            }
        }
    }
}
