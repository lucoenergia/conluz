package org.lucoenergia.conluz.infrastructure.shared.security.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembershipMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.shared.error.ErrorBuilder;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class CommunityContextFilterTest {

    private final CommunityContext communityContext = new CommunityContext();
    private final CommunityContextFilter filter = new CommunityContextFilter(communityContext,
            new ObjectMapper().findAndRegisterModules(), new ErrorBuilder(mock(MessageSource.class)));

    private final Community community = CommunityMother.random().build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anActiveMembershipIsAcceptedInTheHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = filter(callerWith(active(community)), community.getId(), chain);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals(Optional.of(community.getId()), communityContext.getActiveCommunityId());
    }

    @Test
    @DisplayName("MEM-004 a pending membership is refused in X-Community-Id, as no membership is")
    void aPendingMembershipIsRefusedInTheHeader() throws Exception {
        assertRefusedLikeANonMember(callerWith(pending(community)));
    }

    @Test
    @DisplayName("MEM-004 a disabled membership is refused in X-Community-Id, as no membership is")
    void aDisabledMembershipIsRefusedInTheHeader() throws Exception {
        assertRefusedLikeANonMember(callerWith(disabled(community)));
    }

    @Test
    void aSingleActiveMembershipIsSelectedWithoutAHeader() throws Exception {
        filter(callerWith(active(community)), null, new MockFilterChain());

        assertEquals(Optional.of(community.getId()), communityContext.getActiveCommunityId());
    }

    @Test
    @DisplayName("MEM-004 a single pending membership is not selected without a header")
    void aSinglePendingMembershipIsNotSelected() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter(callerWith(pending(community)), null, chain);

        assertNotNull(chain.getRequest());
        assertEquals(Optional.empty(), communityContext.getActiveCommunityId());
    }

    @Test
    @DisplayName("MEM-004 a single disabled membership is not selected without a header")
    void aSingleDisabledMembershipIsNotSelected() throws Exception {
        filter(callerWith(disabled(community)), null, new MockFilterChain());

        assertEquals(Optional.empty(), communityContext.getActiveCommunityId());
    }

    @Test
    @DisplayName("MEM-004 the one active membership is selected next to a pending one")
    void theOneActiveMembershipIsSelectedNextToAPendingOne() throws Exception {
        Community other = CommunityMother.random().build();

        filter(callerWith(pending(other), active(community)), null, new MockFilterChain());

        assertEquals(Optional.of(community.getId()), communityContext.getActiveCommunityId());
    }

    private void assertRefusedLikeANonMember(User caller) throws Exception {
        MockFilterChain nonMemberChain = new MockFilterChain();
        MockHttpServletResponse nonMember = filter(callerWith(), community.getId(), nonMemberChain);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = filter(caller, community.getId(), chain);

        assertEquals(HttpStatus.FORBIDDEN.value(), nonMember.getStatus());
        assertEquals(nonMember.getStatus(), response.getStatus());
        assertNull(nonMemberChain.getRequest());
        assertNull(chain.getRequest());
        assertEquals(Optional.empty(), communityContext.getActiveCommunityId());
    }

    private MockHttpServletResponse filter(User caller, UUID headerCommunityId, MockFilterChain chain)
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(caller, null, caller.getAuthorities()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/anything");
        if (headerCommunityId != null) {
            request.addHeader(CommunityContextFilter.COMMUNITY_ID_HEADER, headerCommunityId.toString());
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static User callerWith(CommunityMembership... memberships) {
        User user = UserMother.randomUser();
        user.setMemberships(Arrays.asList(memberships));
        return user;
    }

    private static CommunityMembership active(Community community) {
        return CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .build();
    }

    private static CommunityMembership pending(Community community) {
        return new CommunityMembership.Builder()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .build();
    }

    private static CommunityMembership disabled(Community community) {
        return CommunityMembershipMother.active()
                .withId(UUID.randomUUID())
                .withCommunity(community)
                .withRole(CommunityRole.COMMUNITY_ADMIN)
                .withEnabled(false)
                .build();
    }
}
