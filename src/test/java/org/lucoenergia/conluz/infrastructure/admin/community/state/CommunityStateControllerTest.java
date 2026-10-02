package org.lucoenergia.conluz.infrastructure.admin.community.state;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enabling and disabling a community are platform-wide actions. Both were previously untested at
 * the controller level, which left the whole 401/403 half of their contract unpinned.
 *
 * <p>Their guards are role checks that make no statement about the community, so every denial is a
 * 403 — including one aimed at a community id that does not exist. A 404 there would mean the guard
 * had grown a visibility gate the {@code hasRole} expression never had.</p>
 */
@Transactional
class CommunityStateControllerTest extends BaseControllerTest {

    private static final String ENABLE = "/api/v1/communities/{communityId}/enable";
    private static final String DISABLE = "/api/v1/communities/{communityId}/disable";

    @Autowired
    private CommunityJpaRepository communityJpaRepository;

    // --- enable ---

    @Test
    void aPlatformAdminEnablesACommunity() throws Exception {
        CommunityEntity community = persistCommunity(false);
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(ENABLE, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        assertTrue(communityJpaRepository.findById(community.getId()).orElseThrow().isEnabled());
    }

    @Test
    void enablingWithoutATokenIsUnauthorized() throws Exception {
        CommunityEntity community = persistCommunity(false);

        mockMvc.perform(post(ENABLE, community.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCommunityAdminMayNotEnableTheirOwnCommunity() throws Exception {
        CommunityEntity community = persistCommunity(false);
        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(post(ENABLE, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        assertFalse(communityJpaRepository.findById(community.getId()).orElseThrow().isEnabled());
    }

    @Test
    void aCommunityMemberMayNotEnableACommunity() throws Exception {
        CommunityEntity community = persistCommunity(false);
        String authHeader = loginAsCommunityMember(community.getId());

        mockMvc.perform(post(ENABLE, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // --- disable ---

    @Test
    void aPlatformAdminDisablesACommunity() throws Exception {
        CommunityEntity community = persistCommunity(true);
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(DISABLE, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());

        assertFalse(communityJpaRepository.findById(community.getId()).orElseThrow().isEnabled());
    }

    @Test
    void disablingWithoutATokenIsUnauthorized() throws Exception {
        CommunityEntity community = persistCommunity(true);

        mockMvc.perform(post(DISABLE, community.getId())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCommunityAdminMayNotDisableTheirOwnCommunity() throws Exception {
        CommunityEntity community = persistCommunity(true);
        String authHeader = loginAsCommunityAdmin(community.getId());

        mockMvc.perform(post(DISABLE, community.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        assertTrue(communityJpaRepository.findById(community.getId()).orElseThrow().isEnabled());
    }

    // --- the role check makes no statement about the community ---

    @Test
    void aCommunityAdminIsForbiddenRatherThanToldTheCommunityIsMissing() throws Exception {
        CommunityEntity own = persistCommunity(true);
        String authHeader = loginAsCommunityAdmin(own.getId());
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(post(ENABLE, unknown)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(DISABLE, unknown)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPlatformAdminIsToldAnUnknownCommunityIsMissing() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(ENABLE, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private CommunityEntity persistCommunity(boolean enabled) {
        return communityJpaRepository.save(CommunityMother.randomEntity().withEnabled(enabled).build());
    }
}
