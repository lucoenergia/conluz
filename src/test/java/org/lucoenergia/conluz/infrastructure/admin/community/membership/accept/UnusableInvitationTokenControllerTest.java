package org.lucoenergia.conluz.infrastructure.admin.community.membership.accept;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.disable.DisableUserService;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every invitation endpoint answers every unusable token the same way, and counts it against the client address.
 */
class UnusableInvitationTokenControllerTest extends BaseInvitationAcceptanceTest {

    @Autowired
    private DisableUserService disableUserService;
    @Autowired
    private CreateOneTimeTokenService createOneTimeTokenService;
    @Autowired
    private MessageSource messageSource;

    enum Endpoint { INSPECT, ACCEPT, ACCEPT_AS_USER }

    enum Unusable { UNKNOWN, MALFORMED, TOO_SHORT, TOO_LONG, EMPTY, EXPIRED, USED, REVOKED, REPLACED,
        ACCEPTED_MEMBERSHIP, DISABLED_ACCOUNT }

    /**
     * An unusable token, and a session to present it with on the authenticated endpoint: its own account's
     * whenever that account can log in, so that the token is refused for its own sake rather than for belonging to
     * someone else.
     */
    private record Case(String token, String bearer) {
    }

    static Stream<Arguments> everyUnusableToken_onEveryEndpoint() {
        return Arrays.stream(Unusable.values())
                .flatMap(unusable -> Arrays.stream(Endpoint.values()).map(endpoint -> Arguments.of(unusable, endpoint)));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("everyUnusableToken_onEveryEndpoint")
    @DisplayName("MEM-015 every unusable token is answered the same 400 on every endpoint, counted and logged once without the token")
    void everyUnusableToken_isAnsweredTheSame400_countedAndLoggedOnce(Unusable unusable, Endpoint endpoint)
            throws Exception {
        Case unusableCase = prepare(unusable);
        String clientIp = "198.51.100." + (unusable.ordinal() * 3 + endpoint.ordinal() + 1);
        logs.clear();
        // The same message whatever the reason, in the locale the server answers in
        String invalidMessage = messageSource.getMessage("error.membership.invitation.token.invalid", null,
                LocaleContextHolder.getLocale());

        String response = send(endpoint, unusableCase, clientIp)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(invalidMessage))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"))
                .andExpect(jsonPath("$.errors[0].message").value(invalidMessage))
                .andExpect(jsonPath("$.errors[0].params").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertNoSecretIn(response);
        assertThat(refusedTokenWarnings()).isEqualTo(1);
        assertThat(logs.allOfThisThread())
                .anyMatch(event -> event.getFormattedMessage().equals(REFUSED_TOKEN_LOG.formatted(clientIp)));
    }

    @Test
    @DisplayName("MEM-015 unusable tokens from one client address are throttled on all three endpoints, with Retry-After")
    void unusableTokensFromOneClientAddress_areThrottledOnEveryEndpoint() throws Exception {
        InvitedAccount withPassword = invitedAccountWithPassword();
        String bearer = loginUser(withPassword.user());
        InvitedMember passwordless = invitedMember();
        String clientIp = "203.0.113.61";

        // The three endpoints share the client address's counter, with failed logins and password resets
        for (int i = 0; i < 20; i++) {
            Endpoint endpoint = Endpoint.values()[i % 3];
            send(endpoint, new Case("unknown-" + i, bearer), clientIp).andExpect(status().isBadRequest());
        }

        // Over the limit, even a usable token is refused before it is looked at
        inspect(passwordless.token(), clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.errors[0].code").value("AUTH_TOO_MANY_FAILED_ATTEMPTS"));
        accept(passwordless.token(), newPassword(), clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        acceptAsUser(bearer, withPassword.member().token(), clientIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        assertThat(linkWorks(passwordless.token())).isTrue();
        assertThat(linkWorks(withPassword.member().token())).isTrue();

        // Another client address is not affected
        accept(passwordless.token(), newPassword(), "203.0.113.62").andExpect(status().isNoContent());
        acceptAsUser(bearer, withPassword.member().token(), "203.0.113.62").andExpect(status().isNoContent());
    }

    private ResultActions send(Endpoint endpoint, Case unusableCase, String clientIp) throws Exception {
        return switch (endpoint) {
            case INSPECT -> inspect(unusableCase.token(), clientIp);
            case ACCEPT -> accept(unusableCase.token(), newPassword(), clientIp);
            case ACCEPT_AS_USER -> acceptAsUser(unusableCase.bearer(), unusableCase.token(), clientIp);
        };
    }

    private Case prepare(Unusable unusable) throws Exception {
        return switch (unusable) {
            case UNKNOWN -> withAnyCaller("A".repeat(43));
            case MALFORMED -> withAnyCaller("%%%not a token%%%");
            case TOO_SHORT -> withAnyCaller("x");
            case TOO_LONG -> withAnyCaller("a".repeat(500));
            case EMPTY -> withAnyCaller("");
            case EXPIRED -> {
                InvitedAccount invited = invitedAccountWithPassword();
                clock.advance(OneTimeTokenPurpose.INVITATION.lifetime());
                yield new Case(invited.member().token(), loginUser(invited.user()));
            }
            case USED -> {
                InvitedAccount invited = invitedAccountWithPassword();
                String bearer = loginUser(invited.user());
                acceptAsUser(bearer, invited.member().token(), "192.0.2.99").andExpect(status().isNoContent());
                yield new Case(invited.member().token(), bearer);
            }
            case REVOKED -> {
                InvitedAccount invited = invitedAccountWithPassword();
                revoke(invited.member()).andExpect(status().isOk());
                yield new Case(invited.member().token(), loginUser(invited.user()));
            }
            case REPLACED -> {
                InvitedAccount invited = invitedAccountWithPassword();
                resend(invited.member()).andExpect(status().isOk());
                tokenFrom(awaitNextEmails(1)[0]);
                yield new Case(invited.member().token(), loginUser(invited.user()));
            }
            case ACCEPTED_MEMBERSHIP -> {
                Community community = newCommunity();
                User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
                // Inviting refuses an accepted membership, so its token is issued through the token store itself
                String token = createOneTimeTokenService.issueForMembership(UserId.of(member.getId()),
                        community.getId(), OneTimeTokenPurpose.INVITATION).value();
                remember(token);
                yield new Case(token, loginUser(member));
            }
            case DISABLED_ACCOUNT -> {
                InvitedAccount invited = invitedAccountWithPassword();
                disableUserService.disable(UserId.of(invited.user().getId()));
                // A disabled account cannot log in, so the token is presented by someone else on the authenticated
                // endpoint
                yield new Case(invited.member().token(), loginUser(newUser()));
            }
        };
    }

    private Case withAnyCaller(String token) throws Exception {
        return new Case(token, loginUser(newUser()));
    }
}
