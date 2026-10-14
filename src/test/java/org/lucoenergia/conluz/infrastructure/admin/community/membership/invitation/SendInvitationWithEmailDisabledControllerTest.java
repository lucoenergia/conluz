package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseRegisterMemberTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SendInvitationWithEmailDisabledControllerTest extends BaseRegisterMemberTest {

    @DynamicPropertySource
    static void emailSendingDisabled(DynamicPropertyRegistry registry) {
        // The public web URL is configured, so the refusal is down to the sending switch alone
        registry.add("conluz.web.public-url", () -> PUBLIC_WEB_URL);
        registry.add("conluz.mail.enabled", () -> "false");
    }

    @Test
    @DisplayName("MEM-009 with email sending disabled the invitation is refused with 503, nothing is issued or sent, "
            + "the invitation instant stays empty, and one warning names only the email category")
    void withEmailSendingDisabled_isRefused() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        UUID userId = registered(bearer, community.getId(), body);
        logs.clear();

        String response = invite(bearer, community.getId(), userId)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();

        assertThat(membershipRow(personalId, community.getId()).get("invited_at")).isNull();
        assertThat(tokenCount(personalId)).isZero();
        verify(emailSender, never()).send(any());
        assertThat(logs.warningsAndAboveOfThisThread())
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .isEqualTo("Email INVITATION not sent: sending is disabled"));
        assertNoSecretIn(response);
    }
}
