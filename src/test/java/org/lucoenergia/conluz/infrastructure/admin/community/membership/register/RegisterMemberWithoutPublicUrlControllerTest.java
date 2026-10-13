package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registering a member when the public web URL is not configured: there is nothing to link an invitation to.
 */
class RegisterMemberWithoutPublicUrlControllerTest extends BaseRegisterMemberTest {

    @DynamicPropertySource
    static void noPublicWebUrl(DynamicPropertyRegistry registry) {
        registry.add("conluz.web.public-url", () -> "");
    }

    @Test
    @DisplayName("MEM-009 without a public web URL the membership is created, nothing is issued or sent, and one "
            + "warning names only the email category")
    void withoutAPublicUrl_theMembershipIsCreated_andNothingIsSent() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        logs.clear();

        register(bearer, community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value(body.get("contactEmail")))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.invitedAt").isEmpty());

        assertThat(membershipRow(personalId, community.getId()).get("invited_at")).isNull();
        assertThat(tokenCount(personalId)).isZero();
        verify(emailSender, never()).send(any());
        assertThat(logs.warningsAndAboveOfThisThread())
                .singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .isEqualTo("Email INVITATION not sent: the public web URL is not configured"));
    }
}
