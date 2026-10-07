package org.lucoenergia.conluz.infrastructure.admin.user.password.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestPasswordResetControllerTest extends BasePasswordResetTest {

    @Test
    void anEligibleUser_isEmailedALinkWithTheTokenInTheFragment_whateverTheTypingOfThePersonalId() throws Exception {
        User user = newUser();
        // Lower case, with a dot, a hyphen and spaces around
        String id = user.getPersonalId().toLowerCase(Locale.ROOT);
        String typed = "  " + id.substring(0, 3) + "." + id.substring(3, 6) + "-" + id.substring(6) + " ";

        MvcResult result = requestRecovery(typed).andExpect(status().isAccepted()).andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        MimeMessage email = awaitEmails(1)[0];
        assertThat(email.getRecipients(Message.RecipientType.TO))
                .extracting(address -> ((InternetAddress) address).getAddress())
                .containsExactly(user.getEmail());
        assertThat(email.getSubject()).isEqualTo("Recupera tu contraseña");
        assertThat(email.getContentType()).startsWith("text/plain");
        String body = bodyOf(email);
        String token = tokenFrom(email);
        assertThat(body).isEqualTo("""
                Hemos recibido una solicitud para restablecer la contraseña de tu cuenta.

                Para elegir una contraseña nueva, abre este enlace:
                %s/reset-password#%s

                El enlace es válido durante 1 día y solo puede usarse una vez.

                Si no lo has solicitado, ignora este mensaje: tu contraseña no cambiará.""".formatted(PUBLIC_WEB_URL, token));
        // The token only travels in the fragment: nothing before the # carries it
        String link = PUBLIC_WEB_URL + "/reset-password#" + token;
        assertThat(link.substring(0, link.indexOf('#'))).doesNotContain(token);
        assertThat(link).doesNotContain("?");

        reset(token, newPassword()).andExpect(status().isNoContent());
        expectOneRequestLogged("SENT", UserPersonalId.mask(user.getPersonalId()));
    }

    @Test
    void anUnknownPersonalId_aUserWithoutEmail_andADisabledUser_areAnsweredExactlyLikeAnEligibleUser()
            throws Exception {
        User eligible = newUser();
        // The column is not nullable: a member imported without an email address has an empty one
        User withoutEmail = newUser(user -> user.setEmail(""));
        User withBlankEmail = newUser(user -> user.setEmail(" "));
        User disabled = newUser(user -> user.setEnabled(false));
        Map<String, String> outcomes = Map.of(
                "UNKNOWN", "UNKNOWNID9",
                "NO_EMAIL", withoutEmail.getPersonalId(),
                "DISABLED", disabled.getPersonalId());

        MvcResult eligibleResult = requestRecovery(eligible.getPersonalId()).andReturn();
        awaitEmails(1);
        clearSpy();

        for (Map.Entry<String, String> outcome : outcomes.entrySet()) {
            logs.clear();

            MvcResult result = requestRecovery(outcome.getValue()).andReturn();

            assertSameResponse(eligibleResult, result);
            verify(emailSender, never()).send(any());
            expectOneRequestLogged(outcome.getKey(), null);
        }
        MvcResult blankEmail = requestRecovery(withBlankEmail.getPersonalId()).andReturn();
        assertSameResponse(eligibleResult, blankEmail);
        verify(emailSender, never()).send(any());
        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);
    }

    @Test
    void aUserIsSentAtMostThreeLinksADay_andOneMoreOnceTheDayHasPassed() throws Exception {
        User user = newUser();

        for (int i = 0; i < 3; i++) {
            requestRecovery(user.getPersonalId()).andExpect(status().isAccepted());
        }
        awaitEmails(3);
        verify(emailSender, times(3)).send(any());

        logs.clear();
        MvcResult overTheLimit = requestRecovery(user.getPersonalId()).andExpect(status().isAccepted()).andReturn();
        assertThat(overTheLimit.getResponse().getContentAsString()).isEmpty();
        verify(emailSender, times(3)).send(any());
        expectOneRequestLogged("OVER_LIMIT", null);

        // Just short of a day after the three links, the limit still holds
        clock.advance(Duration.ofDays(1).minusSeconds(1));
        requestRecovery(user.getPersonalId()).andExpect(status().isAccepted());
        verify(emailSender, times(3)).send(any());

        clock.advance(Duration.ofSeconds(2));
        logs.clear();
        requestRecovery(user.getPersonalId()).andExpect(status().isAccepted());
        verify(emailSender, times(4)).send(any());
        awaitEmails(4);
        expectOneRequestLogged("SENT", null);
    }

    @Test
    void theLimitCountsTheLinksAlreadyStored_soItSurvivesARestart() throws Exception {
        User user = newUser();
        // Tokens stored before the application started again, which keeps nothing in memory
        for (int i = 0; i < 3; i++) {
            issueDirectly(user);
        }

        requestRecovery(user.getPersonalId()).andExpect(status().isAccepted());

        verify(emailSender, never()).send(any());
        expectOneRequestLogged("OVER_LIMIT", null);
    }

    @Test
    void moreRequestsFromOneClientAddressThanItsLimit_areAnswered429_withRetryAfter() throws Exception {
        String clientIp = "203.0.113.41";

        for (int i = 0; i < 20; i++) {
            requestRecovery("UNKNOWN" + i, clientIp).andExpect(status().isAccepted());
        }
        requestRecovery("UNKNOWN20", clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.errors[0].code").value("AUTH_TOO_MANY_FAILED_ATTEMPTS"));

        // Another client address is not affected
        requestRecovery("UNKNOWN21", "203.0.113.42").andExpect(status().isAccepted());
    }

    @Test
    void aSecondRequest_makesTheFirstLinkStopWorking() throws Exception {
        User user = newUser();
        requestRecovery(user.getPersonalId()).andExpect(status().isAccepted());
        String first = tokenFrom(awaitEmails(1)[0]);

        requestRecovery(user.getPersonalId()).andExpect(status().isAccepted());
        String second = tokenFrom(awaitEmails(2)[1]);

        assertThat(second).isNotEqualTo(first);
        reset(first, newPassword())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("USER_PASSWORD_RESET_TOKEN_INVALID"));
        reset(second, newPassword()).andExpect(status().isNoContent());
    }

    @Test
    void anyTokenPresented_isIgnored_evenThatOfAUserWhoMustChangeTheirPassword() throws Exception {
        User flagged = newUser(User::requirePasswordChange);
        String flaggedSession = loginUser(flagged);
        String otherSession = loginUser(newUser());

        for (String authorization : List.of(flaggedSession, otherSession, "Bearer not-a-jwt")) {
            MvcResult result = mockMvc.perform(recovery(flagged.getPersonalId(), "127.0.0.1")
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isAccepted())
                    .andReturn();
            assertThat(result.getResponse().getContentAsString()).as(authorization).isEmpty();
        }

        // Each one was a request like any other
        verify(emailSender, times(3)).send(any());
        awaitEmails(3);
    }

    @Test
    void aMissingPersonalId_isABadRequest() throws Exception {
        mockMvc.perform(post(RECOVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(emailSender, never()).send(any());
    }

    private void clearSpy() {
        clearInvocations(emailSender);
    }

    /**
     * Status, every header and the body, which is empty.
     */
    private static void assertSameResponse(MvcResult expected, MvcResult actual) throws Exception {
        assertThat(actual.getResponse().getStatus()).isEqualTo(expected.getResponse().getStatus()).isEqualTo(202);
        assertThat(actual.getResponse().getHeaderNames())
                .containsExactlyInAnyOrderElementsOf(expected.getResponse().getHeaderNames());
        for (String name : expected.getResponse().getHeaderNames()) {
            assertThat(actual.getResponse().getHeaders(name)).as(name)
                    .isEqualTo(expected.getResponse().getHeaders(name));
        }
        assertThat(actual.getResponse().getContentAsByteArray())
                .isEqualTo(expected.getResponse().getContentAsByteArray())
                .isEmpty();
    }

    private void expectOneRequestLogged(String outcome, String maskedAccount) {
        List<ILoggingEvent> lines = logs.allOfThisThread().stream()
                .filter(event -> event.getLoggerName().equals(RequestPasswordResetServiceImpl.class.getName()))
                .toList();
        assertThat(lines).hasSize(1);
        ILoggingEvent line = lines.get(0);
        assertThat(line.getLevel()).isEqualTo(Level.INFO);
        assertThat(line.getFormattedMessage())
                .startsWith("Password reset requested: account=***")
                .contains("ip=127.0.0.1")
                .endsWith("outcome=" + outcome);
        if (maskedAccount != null) {
            assertThat(line.getFormattedMessage()).contains("account=" + maskedAccount + ",");
        }
    }
}
