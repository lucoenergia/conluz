package org.lucoenergia.conluz.infrastructure.admin.community.membership.invitation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
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

class SendInvitationWithoutPublicUrlControllerTest extends BaseInvitationUnavailableTest {

    @DynamicPropertySource
    static void noPublicWebUrl(DynamicPropertyRegistry registry) {
        registry.add("conluz.web.public-url", () -> "");
        // Sending is enabled, so the refusal is down to the missing URL alone
        registry.add("conluz.mail.enabled", () -> "true");
    }

    @Override
    protected String expectedWarning() {
        return "Email INVITATION not sent: the public web URL is not configured";
    }

    @Test
    @DisplayName("MEM-009 without a public web URL the invitation is refused with 503, nothing is issued or sent, "
            + "the invitation instant stays empty, and one warning names only the email category")
    void withoutAPublicUrl_isRefused() throws Exception {
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
                        .isEqualTo("Email INVITATION not sent: the public web URL is not configured"));
        assertNoSecretIn(response);
    }
}
