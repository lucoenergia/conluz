package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.auth.AuthRepository;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A membership's investment is personal financial data. It is exposed by exactly one endpoint --
 * the payback read, under its own guard -- and must not ride along on any response that already
 * carries membership information to a wider audience, nor on the token itself.
 *
 * <p>These are regression tests rather than tests of new behaviour: nothing was added to those
 * responses, and the point is that nothing ever is. Adding the field to the domain object is what
 * makes the mistake easy, because the mapping sites that would leak it are the same ones that were
 * just edited to carry it into persistence. Each test asserts on the raw response body, so it
 * fails whatever the field ends up being called or nested under.
 */
@Transactional
class MembershipInvestmentDoesNotLeakTest extends BaseControllerTest {

    private static final BigDecimal INVESTMENT = new BigDecimal("1500.00");

    @Autowired
    private CommunityJpaRepository communityJpaRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private AuthRepository authRepository;
    @Autowired
    private GetMembershipsRepository getMembershipsRepository;

    @Test
    void theMembershipsListingOfACommunityNeverCarriesTheInvestment() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMemberWithInvestment(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        String body = mockMvc.perform(get("/api/v1/communities/{communityId}/memberships", community.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The member must be in the listing, or the assertion below would pass vacuously.
        assertTrue(body.contains(member.getId().toString()),
                () -> "the member is missing from the listing: " + body);
        assertNoInvestment(body);
    }

    @Test
    void theCurrentUserEndpointNeverCarriesTheInvestment() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMemberWithInvestment(community.getId());
        String memberToken = loginUser(member);

        String body = mockMvc.perform(get("/api/v1/users/current")
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(community.getId().toString()),
                () -> "the membership is missing from the current user: " + body);
        assertNoInvestment(body);
    }

    @Test
    void theUserByIdEndpointNeverCarriesTheInvestment() throws Exception {
        CommunityEntity community = persistCommunity();
        User member = persistMemberWithInvestment(community.getId());
        String adminToken = loginAsCommunityAdmin(community.getId());

        String body = mockMvc.perform(get("/api/v1/users/{userId}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains(community.getId().toString()),
                () -> "the membership is missing from the user: " + body);
        assertNoInvestment(body);
    }

    /**
     * The token travels to the client and its payload is readable by anyone holding it, so an
     * investment in any claim would be public to the bearer regardless of any endpoint guard.
     * The whole payload is decoded rather than read through
     * {@link AuthRepository#getCommunityMemberships}, because that accessor's {@code Map&lt;String,
     * String&gt;} shape could not carry an amount even if a claim did.
     */
    @Test
    void theJwtPayloadNeverCarriesTheInvestment() {
        CommunityEntity community = persistCommunity();
        User member = persistMemberWithInvestment(community.getId());
        // The claims are built from the principal's memberships, so they have to be loaded first.
        member.setMemberships(getMembershipsRepository.findByUserId(member.getId()));

        String payload = decodePayload(authRepository.getToken(member).getToken());

        assertTrue(payload.contains(community.getId().toString()),
                () -> "the membership is missing from the payload: " + payload);
        assertNoInvestment(payload);
    }

    /**
     * The middle segment of a JWS is its base64url-encoded claim set.
     */
    private static String decodePayload(String token) {
        String[] segments = token.split("\\.");
        return new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
    }

    /**
     * The detector itself, because it was narrowed to fix a flake and a narrowing can go too far.
     *
     * <p>The amount must still be caught wherever it is nested and under either rendering, while a
     * hexadecimal id that merely contains the digits must not be. The second case is not
     * hypothetical: it is what made this test fail at random before, since the payload carries
     * UUIDs and {@code 1500} appears inside one regularly.</p>
     */
    @Test
    void theDetectorCatchesTheAmountButNotAUuidThatMerelyContainsTheDigits() {
        assertThrows(AssertionError.class,
                () -> assertNoInvestment("{\"claims\":{\"nested\":[{\"x\":1500.00}]}}"));
        assertThrows(AssertionError.class,
                () -> assertNoInvestment("{\"claims\":{\"nested\":[{\"x\":\"1500\"}]}}"));
        assertThrows(AssertionError.class,
                () -> assertNoInvestment("{\"x\":\"1500.00\"}"));

        assertNoInvestment("{\"sub\":\"a1500f2e-0000-4000-8000-000000001500\",\"communities\":[\"1500abcd\"]}");
    }

    /**
     * Checks for the field under any name, and for the amount under either rendering.
     *
     * <p>The amount is compared against the JSON's <em>values</em> rather than against the raw text.
     * A substring search cannot express "1500 appears as a number here": the payload is full of
     * UUIDs, a UUID is hexadecimal, and {@code 1500} turns up inside one often enough to fail a
     * run at random. This walks the parsed document instead, so both {@code 1500.00} and
     * {@code 1500} are caught wherever they are nested, and a hex digit sequence is not.</p>
     */
    private void assertNoInvestment(String payload) {
        assertFalse(payload.toLowerCase(Locale.ROOT).contains("investment"),
                () -> "the payload mentions an investment: " + payload);
        assertNoValueEquals(readTree(payload), payload);
    }

    private void assertNoValueEquals(JsonNode node, String payload) {
        if (node.isObject() || node.isArray()) {
            node.forEach(child -> assertNoValueEquals(child, payload));
            return;
        }
        if (node.isNumber()) {
            assertFalse(INVESTMENT.compareTo(node.decimalValue()) == 0,
                    () -> "the payload carries the investment amount: " + payload);
            return;
        }
        if (node.isTextual()) {
            String text = node.asText();
            assertFalse(text.equals(INVESTMENT.toPlainString())
                            || text.equals(INVESTMENT.stripTrailingZeros().toPlainString()),
                    () -> "the payload carries the investment amount: " + payload);
        }
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new IllegalStateException("the JWT payload is not JSON: " + payload, e);
        }
    }

    private CommunityEntity persistCommunity() {
        return communityJpaRepository.save(CommunityMother.randomEntity().build());
    }

    /**
     * Creates an enabled member of the community and records an investment on the membership
     * directly, since the endpoint that does so is introduced in a later change.
     */
    private User persistMemberWithInvestment(UUID communityId) {
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipService.create(communityId, member.getId(), CommunityRole.COMMUNITY_MEMBER);

        CommunityMembershipEntity membership = membershipJpaRepository
                .findByUserIdAndCommunityId(member.getId(), communityId)
                .orElseThrow();
        membership.setInvestmentEur(INVESTMENT);
        membershipJpaRepository.save(membership);

        return member;
    }
}
