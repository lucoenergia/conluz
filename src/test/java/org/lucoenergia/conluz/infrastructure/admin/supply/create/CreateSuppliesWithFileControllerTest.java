package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.JsonNode;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSuppliesWithFileControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    private static final String URL = "/api/v1/supplies/import";
    public static final String SUPPLIES_CSV = "fixtures/supplies/supplies.csv";
    public static final String SUPPLIES_BAD_FORMAT_CSV = "fixtures/supplies/supplies_bad_format.csv";
    public static final String EMPTY_CSV = "fixtures/empty.csv";
    public static final String SUPPLIES_MALFORMED_CSV = "fixtures/supplies/supplies_malformed.csv";
    public static final String MULTIPART_FILE_NAME = "file";
    public static final String TEXT_CSV_MEDIA_TYPE = "text/csv";

    @ParameterizedTest
    @ValueSource(strings = {"12345678a", "\"12345678 A\"", "12345678-A", "12.345.678-A"})
    void testImportResolvesTheOwnerFromATypingVariantOfTheirPersonalId(String variant) throws Exception {

        User owner = UserMother.randomUserWithPersonalId("12345678A");
        createUserRepository.create(owner);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        String csv = "code,addressRef,address,partitionCoefficient,personalId\n"
                + "ES0033333333333333FF0F,A9384752345OA124,Main St,0.078632," + variant + "\n";
        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME, "supplies.csv", TEXT_CSV_MEDIA_TYPE, csv.getBytes(StandardCharsets.UTF_8));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasItem("ES0033333333333333FF0F")))
                .andExpect(jsonPath("$.errors", hasSize(0)));

        UUID storedOwnerId = supplyRepository.findByCode("ES0033333333333333FF0F").orElseThrow().getUser().getId();
        Assertions.assertEquals(owner.getId(), storedOwnerId);
    }

    /**
     * #341: a row whose owner is a member of another community gets exactly the per-row error of a
     * row whose personalId matches no user, and the rest of the file is still processed.
     */
    @Test
    void testImportReportsAMemberOfAnotherCommunityExactlyLikeAnUnknownPersonalId() throws Exception {

        Community communityA = createCommunityRepository.create(CommunityMother.random().build());

        User memberOfB = UserMother.randomUser();
        createUserRepository.create(memberOfB);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, memberOfB.getId(), CommunityRole.COMMUNITY_MEMBER);
        User memberOfA = UserMother.randomUser();
        createUserRepository.create(memberOfA);
        createMembershipService.create(communityA.getId(), memberOfA.getId(), CommunityRole.COMMUNITY_MEMBER);
        String unknownPersonalId = UserMother.randomUser().getPersonalId();

        String csv = "code,addressRef,address,partitionCoefficient,personalId\n"
                + "ES0033333333333333LL0L,A9384752345OA124,Main St,0.078632," + memberOfB.getPersonalId() + "\n"
                + "ES0033333333333333MM0M,A9384752345OA125,Main St,0.078632," + unknownPersonalId + "\n"
                + "ES0033333333333333NN0N,A9384752345OA126,Main St,0.078632," + memberOfA.getPersonalId() + "\n";
        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME, "supplies.csv", TEXT_CSV_MEDIA_TYPE, csv.getBytes(StandardCharsets.UTF_8));

        String authHeader = loginAsCommunityAdmin(communityA.getId());

        String response = mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", communityA.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.created", hasItem("ES0033333333333333NN0N")))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andReturn().getResponse().getContentAsString();

        Map<String, String> errorByCode = new HashMap<>();
        for (JsonNode error : objectMapper.readTree(response).get("errors")) {
            errorByCode.put(error.get("item").asText(), error.get("errorMessage").asText());
        }
        String memberOfBError = errorByCode.get("ES0033333333333333LL0L");
        String unknownError = errorByCode.get("ES0033333333333333MM0M");
        Assertions.assertNotNull(memberOfBError);
        Assertions.assertNotNull(unknownError);
        // The message quotes the personalId of the row, the only part that may differ.
        Assertions.assertTrue(memberOfBError.contains(memberOfB.getPersonalId()));
        Assertions.assertTrue(unknownError.contains(unknownPersonalId));
        Assertions.assertEquals(
                unknownError.replace(unknownPersonalId, "{personalId}"),
                memberOfBError.replace(memberOfB.getPersonalId(), "{personalId}"));

        Assertions.assertTrue(getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333LL0L")).isEmpty());
        Assertions.assertTrue(getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333MM0M")).isEmpty());
        Supply created = getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333NN0N")).orElseThrow();
        Assertions.assertEquals(memberOfA.getId(), created.getUser().getId());
        Assertions.assertEquals(communityA.getId(), created.getCommunity().getId());
    }

    @Test
    void testMinimumBody() throws Exception {

        String userPersonalId = "987654321S";
        User user = UserMother.randomUserWithId(UUID.fromString("e7ab39cd-9250-40a9-b829-f11f65aae27d"));
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);

        String supplyCode = "ES002100823465";
        Supply supply = new Supply.Builder()
                .withId(UUID.randomUUID())
                .withCode(supplyCode)
                .withAddress(RandomStringUtils.random(20, true, true))
                .withEnabled(true)
                .withName(RandomStringUtils.random(20, true, true))
                .withUser(user)
                .build();
        createSupplyRepository.create(supply, UserId.of(user.getId()));

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created").isNotEmpty())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.created", hasItem("ES004567891234")))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.errors[*].errorMessage", hasItem("El punto de suministro con CUPS 'ES002100823465' ya existe.")))
                .andExpect(jsonPath("$.errors[*].item", hasItem("ES007890123456")))
                .andExpect(jsonPath("$.errors[*].errorMessage", hasItem("El usuario con identificador '456123789D' no ha sido encontrado. Revise que el identificador sea correcto.")))
                .andExpect(jsonPath("$.errors[*].errorMessage", not(hasItem("No ha sido posible crear el punto de suministro con los datos proporcionados."))));
    }

    @Test
    void testWithWrongFormat() throws Exception {

        ClassPathResource resource = new ClassPathResource(SUPPLIES_BAD_FORMAT_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
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
    void testWithWrongContentType() throws Exception {

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
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

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
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
                MULTIPART_FILE_NAME,
                "users.txt",
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
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

        ClassPathResource resource = new ClassPathResource(EMPTY_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
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

        ClassPathResource resource = new ClassPathResource(SUPPLIES_MALFORMED_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
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
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

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

        ClassPathResource resource = new ClassPathResource(SUPPLIES_CSV);

        MockMultipartFile file = new MockMultipartFile(
                MULTIPART_FILE_NAME,
                SUPPLIES_CSV,
                TEXT_CSV_MEDIA_TYPE,
                Files.readAllBytes(resource.getFile().toPath()));

        mockMvc.perform(multipart(URL)
                        .file(file)
                        .param("communityId", DEFAULT_COMMUNITY_ID.toString())
                        .header(HttpHeaders.AUTHORIZATION, authHeader))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }
}
