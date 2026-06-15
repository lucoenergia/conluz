package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.SharingAgreementRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.random;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSharingAgreementControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/sharing-agreements";

    @Autowired
    private SharingAgreementRepository sharingAgreementRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void testCreateSharingAgreement() throws Exception {
        Community community = createCommunityRepository.create(random().build());

        User communityAdmin = UserMother.randomUser();
        communityAdmin.enable();
        createUserRepository.create(communityAdmin);
        createMembershipService.create(community.getId(), communityAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);
        String authHeader = loginUser(communityAdmin);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s",
                  "communityId": "%s"
                }
                """, startDate, endDate, community.getId());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.communityId").value(community.getId().toString()));

        Assertions.assertEquals(1, sharingAgreementRepository.count());
    }

    @Test
    void testCommunityAdminCanCreateForOwnCommunity() throws Exception {
        loginAsDefaultPlatformAdmin();

        Community community = createCommunityRepository.create(random().build());

        User communityAdmin = UserMother.randomUser();
        communityAdmin.enable();
        createUserRepository.create(communityAdmin);
        createMembershipService.create(community.getId(), communityAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);

        String authHeader = loginUser(communityAdmin);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s",
                  "communityId": "%s"
                }
                """, startDate, endDate, community.getId());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.communityId").value(community.getId().toString()));

        Assertions.assertEquals(1, sharingAgreementRepository.count());
    }

    @Test
    void testCommunityAdminCannotCreateForOtherCommunityGetsNotFound() throws Exception {
        loginAsDefaultPlatformAdmin();

        Community ownCommunity = createCommunityRepository.create(random().build());
        Community otherCommunity = createCommunityRepository.create(random().build());

        User communityAdmin = UserMother.randomUser();
        communityAdmin.enable();
        createUserRepository.create(communityAdmin);
        createMembershipService.create(ownCommunity.getId(), communityAdmin.getId(), CommunityRole.COMMUNITY_ADMIN);

        String authHeader = loginUser(communityAdmin);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s",
                  "communityId": "%s"
                }
                """, startDate, endDate, otherCommunity.getId());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithMissingRequiredFields() {
        UUID communityId = UUID.randomUUID();
        return List.of(
                String.format("""
                {
                  "endDate": "2023-12-31",
                  "communityId": "%s"
                }
                """, communityId),
                """
                {
                  "startDate": "2023-01-01",
                  "endDate": "2023-12-31"
                }
                """,
                """
                {
                }
                """
        );
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    static List<String> getBodyWithInvalidFormatValues() {
        UUID communityId = UUID.randomUUID();
        return List.of(
                String.format("""
                {
                  "startDate": "invalid-date",
                  "endDate": "2023-12-31",
                  "communityId": "%s"
                }
                """, communityId),
                String.format("""
                {
                  "startDate": "2023-01-01",
                  "endDate": "invalid-date",
                  "communityId": "%s"
                }
                """, communityId),
                String.format("""
                {
                  "startDate": "01/01/2023",
                  "endDate": "31/12/2023",
                  "communityId": "%s"
                }
                """, communityId)
        );
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testAuthenticatedUserWithoutAdminRoleCannotAccess() throws Exception {

        String authHeader = loginAsPartner();

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(6);

        String body = String.format("""
                {
                  "startDate": "%s",
                  "endDate": "%s",
                  "communityId": "%s"
                }
                """, startDate, endDate, UUID.randomUUID());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
