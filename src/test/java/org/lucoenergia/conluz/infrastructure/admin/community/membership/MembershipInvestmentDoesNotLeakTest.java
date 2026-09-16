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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
     * Checks for the field under any name and for the amount under either rendering. The amount is
     * matched with its decimal point rather than as a bare {@code 1500}, because a UUID is
     * hexadecimal and would collide with the digits alone often enough to make this flaky.
     */
    private void assertNoInvestment(String payload) {
        assertFalse(payload.toLowerCase(Locale.ROOT).contains("investment"),
                () -> "the payload mentions an investment: " + payload);
        assertFalse(payload.contains(INVESTMENT.toPlainString()),
                () -> "the payload carries the investment amount: " + payload);
        assertFalse(payload.contains(INVESTMENT.stripTrailingZeros().toPlainString()),
                () -> "the payload carries the investment amount: " + payload);
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
                .findByUserId(member.getId()).stream()
                .filter(m -> communityId.equals(m.getCommunity().getId()))
                .findFirst()
                .orElseThrow();
        membership.setInvestmentEur(INVESTMENT);
        membershipJpaRepository.save(membership);

        return member;
    }
}
