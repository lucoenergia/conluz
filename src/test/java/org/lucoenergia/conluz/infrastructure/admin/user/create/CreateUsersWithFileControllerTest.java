package org.lucoenergia.conluz.infrastructure.admin.user.create;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateUsersWithFileControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users/import";

    private static final String HEADER =
            "number,fullName,personalId,address,email,phoneNumber,role,password,communityId,communityRole\n";

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private GetMembershipsRepository getMembershipsRepository;
    @Autowired
    private MessageSource messageSource;

    @Test
    void testMinimumBody() throws Exception {

        ClassPathResource resource = new ClassPathResource("fixtures/users/users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixtures/users/users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void testWithWrongContentType() throws Exception {

        ClassPathResource resource = new ClassPathResource("fixtures/users/users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixtures/users/users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
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
    void testWithWrongFileMimeType() throws Exception {

        ClassPathResource resource = new ClassPathResource("fixtures/users/users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixtures/users/users.csv",
                "application/octet-stream",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithWrongFileExtension() throws Exception {

        ClassPathResource resource = new ClassPathResource("application-test.properties");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.txt",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithEmptyCsvFile() throws Exception {

        ClassPathResource resource = new ClassPathResource("fixtures/empty.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixtures/users/users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void testWithMalformedCsvFile() throws Exception {

        ClassPathResource resource = new ClassPathResource("fixtures/users/users_malformed.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixtures/users/users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void
    testWithoutFile() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithoutToken() throws Exception {

        mockMvc.perform(post(URL))
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

        ClassPathResource resource = new ClassPathResource("fixtures/users/users.csv");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fixtures/users/users.csv",
                "text/csv",
                Files.readAllBytes(resource.getFile().toPath()));

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    void testWithCommunityColumns() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());

        String csvContent = "number,fullName,personalId,address,email,phoneNumber,role,password,communityId,communityRole\n" +
                "1,Test User,111111111A,1 Test St,test.user@example.com,600000001,partner,password1," +
                community.getId() + ",COMMUNITY_MEMBER\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users_with_community.csv",
                "text/csv",
                csvContent.getBytes());

        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", community.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void testCommunityAdminCannotImportRowIntoAnotherCommunity() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, "334000001A", communityB.getId().toString(), "COMMUNITY_ADMIN");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].personalId").value("334000001A"))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(communityMismatchMessage()));

        assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of("334000001A")).isEmpty());
        assertTrue(getMembershipsRepository.findByCommunityId(communityB.getId()).isEmpty());
    }

    @Test
    void testCommunityAdminImportsRowsWithOwnOrNoCommunityIntoOwnCommunity() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, "334000002A", communityA.getId().toString(), "COMMUNITY_MEMBER") +
                row(2, "334000003A", "", "");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", containsInAnyOrder("334000002A", "334000003A")))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertMemberOf("334000002A", communityA.getId());
        assertMemberOf("334000003A", communityA.getId());
    }

    @Test
    void testRowWithWhitespaceOnlyCommunityIsImportedIntoImportCommunity() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, "334000004A", "   ", "COMMUNITY_MEMBER");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", containsInAnyOrder("334000004A")))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertMemberOf("334000004A", communityA.getId());
    }

    @Test
    void testCommunityAdminCannotImportIntoAnotherCommunityQueryParameter() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, "334000005A", communityB.getId().toString(), "COMMUNITY_MEMBER") +
                row(2, "334000006A", "", "");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityB.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound());

        assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of("334000005A")).isEmpty());
        assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of("334000006A")).isEmpty());
        assertTrue(getMembershipsRepository.findByCommunityId(communityB.getId()).isEmpty());
    }

    @Test
    void testMixedFileCreatesValidRowsAndReportsOnlyCrossCommunityRows() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, "334000007A", communityA.getId().toString(), "COMMUNITY_MEMBER") +
                row(2, "334000008A", communityB.getId().toString(), "COMMUNITY_ADMIN") +
                row(3, "334000009A", "", "");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", containsInAnyOrder("334000007A", "334000009A")))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].personalId").value("334000008A"))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(communityMismatchMessage()));

        assertMemberOf("334000007A", communityA.getId());
        assertMemberOf("334000009A", communityA.getId());
        assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of("334000008A")).isEmpty());
        assertTrue(getMembershipsRepository.findByCommunityId(communityB.getId()).isEmpty());
    }

    @Test
    void testPlatformAdminCannotImportRowIntoAnotherCommunity() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsDefaultPlatformAdmin();

        String csvContent = HEADER +
                row(1, "334000010A", communityB.getId().toString(), "COMMUNITY_MEMBER");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].personalId").value("334000010A"))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(communityMismatchMessage()));

        assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of("334000010A")).isEmpty());
        assertTrue(getMembershipsRepository.findByCommunityId(communityB.getId()).isEmpty());
    }

    @Test
    void testMalformedRowCommunityIsReportedPerRow() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, "334000011A", "not-a-uuid", "COMMUNITY_MEMBER") +
                row(2, "334000012A", communityA.getId().toString(), "COMMUNITY_MEMBER");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", containsInAnyOrder("334000012A")))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].personalId").value("334000011A"))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(communityMismatchMessage()));

        assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of("334000011A")).isEmpty());
        assertMemberOf("334000012A", communityA.getId());
    }

    @Test
    void testCrossCommunityRowForExistingUserDoesNotRevealThatTheUserExists() throws Exception {
        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        Community communityB = createCommunityRepository.create(CommunityMother.random().build());
        User existingUser = UserMother.randomUser();
        createUserRepository.create(existingUser);
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String csvContent = HEADER +
                row(1, existingUser.getPersonalId(), communityB.getId().toString(), "COMMUNITY_MEMBER");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isEmpty())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].personalId").value(existingUser.getPersonalId()))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(communityMismatchMessage()));

        assertTrue(getMembershipsRepository.findByCommunityId(communityB.getId()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"331000001a", "\"331000001 A\"", "331000001-A", "331.000.001-A"})
    void testRowWithAVariantOfAnExistingPersonalIdIsReportedWithoutTheValueAndOtherRowsAreCreated(String variant)
            throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        createUserRepository.create(UserMother.randomUserWithPersonalId("331000001A"));
        String authHeader = loginAsDefaultPlatformAdmin();

        String csvContent = HEADER +
                row(1, variant, "", "") +
                row(2, "331000002A", "", "");

        mockMvc.perform(multipart(URL)
                        .file(csv(csvContent))
                        .param("communityId", community.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", containsInAnyOrder("331000002A")))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].errorMessage").value(userAlreadyExistsMessage()))
                .andExpect(jsonPath("$.errors[0].errorMessage", not(containsString("331"))));

        assertMemberOf("331000002A", community.getId());
    }

    private static String row(int number, String personalId, String communityId, String communityRole) {
        return number + ",Test User " + number + "," + personalId + ",1 Test St,user" + number + "@example.com,"
                + "60000000" + number + ",partner,password" + number + "," + communityId + "," + communityRole + "\n";
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "users_with_community.csv", "text/csv", content.getBytes());
    }

    private String userAlreadyExistsMessage() {
        return messageSource.getMessage("error.user.already.exists", new Object[0], LocaleContextHolder.getLocale());
    }

    private String communityMismatchMessage() {
        return messageSource.getMessage("error.user.import.community.mismatch", new Object[0],
                LocaleContextHolder.getLocale());
    }

    private void assertMemberOf(String personalId, UUID communityId) {
        User user = getUserRepository.findByPersonalId(UserPersonalId.of(personalId)).orElseThrow();
        assertTrue(getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), communityId).isPresent());
    }
}
