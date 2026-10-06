package org.lucoenergia.conluz.infrastructure.shared.security;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.JwtAuthenticationFilter;
import org.lucoenergia.conluz.infrastructure.shared.security.auth.PasswordChangeRequiredFilter;
import org.lucoenergia.conluz.infrastructure.shared.security.community.CommunityContextFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebSecurityConfigTest extends BaseControllerTest {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    void aUserWhoMustChangeTheirPassword_isRefusedRightAfterAuthentication_beforeCommunityResolution() {
        List<Class<?>> filters = filterChainProxy.getFilterChains().get(0).getFilters().stream()
                .map(Filter::getClass)
                .<Class<?>>map(type -> type)
                .toList();

        int authentication = filters.indexOf(JwtAuthenticationFilter.class);
        int refusal = filters.indexOf(PasswordChangeRequiredFilter.class);
        int community = filters.indexOf(CommunityContextFilter.class);
        Assertions.assertTrue(authentication >= 0 && refusal == authentication + 1 && community == refusal + 1,
                () -> "unexpected filter order: " + filters);
    }

    @Test
    void testSecurityFilterChainForApiDocs() throws Exception {
        mockMvc.perform(get("/api-docs")).andExpect(status().isOk());
    }

    @Test
    void testSecurityFilterChainForHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void testSecurityFilterChainForInfo() throws Exception {
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }
}
