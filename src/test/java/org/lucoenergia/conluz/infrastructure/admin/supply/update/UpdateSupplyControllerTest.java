package org.lucoenergia.conluz.infrastructure.admin.supply.update;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class UpdateSupplyControllerTest extends BaseControllerTest {

    private static final String PATH = "/api/v1/supplies";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DateConverter dateConverter;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private GetSupplyRepository getSupplyRepository;

    @Test
    void testUpdateSupplyModifyingAll() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        // Creates two users
        User userOne = UserMother.randomUser();
        userOne = createUserRepository.create(userOne);

        // Creates one supply
        Supply supply = SupplyMother.random().build();
        supply = createSupplyRepository.create(supply, UserId.of(userOne.getId()), DEFAULT_COMMUNITY_ID);

        // Modify data of the supply
        UpdateSupplyBody supplyModified = new UpdateSupplyBody();
        supplyModified.setCode(SupplyMother.randomCups());
        supplyModified.setName("name");
        supplyModified.setAddress("address");
        supplyModified.setAddressRef("4ASDF654ASDF89ASD");

        String bodyAsString = objectMapper.writeValueAsString(supplyModified);

        mockMvc.perform(put(String.format("%s/%s", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyAsString))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(supplyModified.getCode()))
                .andExpect(jsonPath("$.name").value(supplyModified.getName()))
                .andExpect(jsonPath("$.address").value(supplyModified.getAddress()))
                .andExpect(jsonPath("$.addressRef").value(supplyModified.getAddressRef()))
                .andExpect(jsonPath("$.enabled").value(supply.getEnabled()))
                .andExpect(jsonPath("$.contract.validDateFrom").value(supply.getContract().getValidDateFrom().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
                .andExpect(jsonPath("$.distributor.name").value(supply.getDistributor().getName()))
                .andExpect(jsonPath("$.distributor.code").value(supply.getDistributor().getCode()))
                .andExpect(jsonPath("$.distributor.pointType").value(supply.getDistributor().getPointType()))
                .andExpect(jsonPath("$.shelly.macAddress").value(supply.getShelly().getMacAddress()))
                .andExpect(jsonPath("$.shelly.id").value(supply.getShelly().getId()))
                .andExpect(jsonPath("$.shelly.mqttPrefix").value(supply.getShelly().getMqttPrefix()))
                .andExpect(jsonPath("$.user.id").value(userOne.getId().toString()))
                .andExpect(jsonPath("$.user.personalId").doesNotExist());
    }

    /**
     * addressRef is absent from the schema's requiredProperties, and this endpoint documents that an
     * omitted optional property is written as null. Both require the server to accept its absence.
     */
    @Test
    void testUpdateSupplyWithoutAddressRefClearsIt() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), DEFAULT_COMMUNITY_ID);

        UpdateSupplyBody supplyModified = new UpdateSupplyBody();
        supplyModified.setCode(SupplyMother.randomCups());
        supplyModified.setAddress("address");

        mockMvc.perform(put(String.format("%s/%s", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supplyModified)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("address"))
                .andExpect(jsonPath("$.addressRef").isEmpty());
    }

    @Test
    void testUpdateSupplyReportsTheOwningCommunity() throws Exception {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        String authHeader = loginAsCommunityAdmin(community.getId());

        User user = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(user).build(),
                UserId.of(user.getId()), community.getId());

        UpdateSupplyBody supplyModified = new UpdateSupplyBody();
        supplyModified.setCode(SupplyMother.randomCups());
        supplyModified.setName("name");
        supplyModified.setAddress("address");
        supplyModified.setAddressRef("4ASDF654ASDF89ASD");

        mockMvc.perform(put(String.format("%s/%s", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supplyModified)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.id").value(community.getId().toString()))
                .andExpect(jsonPath("$.community.name").value(community.getName()));
    }

    @Test
    void testWithUnknownSupply() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String supplyId = UUID.randomUUID().toString();

        String body = """
                        {
                          "code": "code",
                          "name": "my home",
                          "address": "Fake Street 123",
                          "addressRef": "4ASDF654ASDF89ASD"
                        }
                """;

        mockMvc.perform(put(PATH + "/" + supplyId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void testWithUnknownFields() throws Exception {

        final String body = """
                        {
                          "unknown": 1,
                          "code": "code",
                          "address": "Fake Street 123",
                          "addressRef": "4ASDF654ASDF89ASD"
                        }
                """;

        final String authHeader = loginAsDefaultPlatformAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
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

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        final String authHeader = loginAsDefaultPlatformAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
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
        return List.of(
                """
                                {
                                  "address": "Fake Street 123",
                                  "addressRef": "4ASDF654ASDF89ASD"
                                }
                        """,
                """
                                {
                                  "code": "code",
                                  "addressRef": "4ASDF654ASDF89ASD"
                                }
                        """,
                """
                                {
                                  "code": "code",
                                  "addressRef": "4ASDF654ASDF89ASD",
                                  "address": "Fake Street 123",
                                }
                        """);
    }

    @ParameterizedTest
    @MethodSource("getBodyWithInvalidFormatValues")
    void testWithInvalidFormatValues(String body) throws Exception {

        final String authHeader = loginAsDefaultPlatformAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
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
        return List.of("""
                            {
                                 "code": "ES0031300326337001WS0F",
                                 "address": "MAYOR 1",
                                 "addressRef": "4ASDF654ASDF89ASD",
                                 "personalId": "personalId",
                                 "enabled": true,
                                 "validDateFrom": "2023-05-01"
                             }
                        """,
                """
                            {
                                 "code": "ES0031300326337001WS0F",
                                 "address": "MAYOR 1",
                                 "addressRef": "4ASDF654ASDF89ASD",
                                 "personalId": "personalId",
                                 "enabled": true,
                                 "pointType": "foo"
                             }
                        """,
                """
                            {
                                 "code": "ES0031300326337001WS0F",
                                 "address": "MAYOR 1",
                                 "addressRef": "4ASDF654ASDF89ASD",
                                 "personalId": "personalId",
                                 "enabled": "foo"
                             }
                        """);
    }

    @Test
    void
    testWithoutBody() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

        final String supplyId = UUID.randomUUID().toString();

        mockMvc.perform(put(PATH + "/" + supplyId)
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
    void
    testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(put(PATH)
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

        mockMvc.perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("SUP-006 a code stored before the CUPS rules is kept when the update leaves it unchanged")
    void testUpdateSupplyKeepingALegacyCodeSucceeds() throws Exception {
        Supply supply = existingSupply("legacy code 7", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        putSupply(authHeader, supply, "legacy code 7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("legacy code 7"))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.address").value("New address"));
    }

    @ParameterizedTest
    @DisplayName("SUP-006 a changed code that is not a CUPS is refused with 400 SUPPLY_CUPS_INVALID, naming no code, "
            + "and the supply is not changed")
    @ValueSource(strings = {"ES0031300325733001F", "ES0031300325733001FH0FX", "ES00313003257330 1FH0F",
            "legacy code 8"})
    void testUpdateSupplyChangingTheCodeToANonCupsIsRefused(String code) throws Exception {
        Supply supply = existingSupply("ES0031300325733011FH0F", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String response = putSupply(authHeader, supply, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_CUPS_INVALID"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(response.contains(code));
        Supply stored = getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow();
        Assertions.assertEquals("ES0031300325733011FH0F", stored.getCode());
        Assertions.assertEquals(supply.getName(), stored.getName());
    }

    @Test
    @DisplayName("SUP-006 a changed lower-case code is stored upper-cased")
    void testUpdateSupplyStoresAChangedCodeUpperCased() throws Exception {
        Supply supply = existingSupply("ES0031300325733012FH0F", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        putSupply(authHeader, supply, "es0031300325733013fh")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ES0031300325733013FH"));

        Assertions.assertEquals("ES0031300325733013FH",
                getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow().getCode());
    }

    @ParameterizedTest
    @DisplayName("SUP-007 changing a code to the CUPS of another supply of the community, in either length and in "
            + "any case, is refused with 400 SUPPLY_ALREADY_EXISTS, naming no code")
    @CsvSource({
            "ES0031300325733014FH, ES0031300325733014FH0F",
            "ES0031300325733014FH0F, ES0031300325733014FH",
            "ES0031300325733014FH0F, es0031300325733014fh0f"
    })
    void testUpdateSupplyChangingTheCodeToADuplicateInTheCommunityIsRefused(String other, String submitted)
            throws Exception {
        existingSupply(other, DEFAULT_COMMUNITY_ID);
        Supply supply = existingSupply("ES0031300325733015FH0F", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String response = putSupply(authHeader, supply, submitted)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(response.toUpperCase().contains(submitted.toUpperCase()));
        Assertions.assertEquals("ES0031300325733015FH0F",
                getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow().getCode());
    }

    @Test
    @DisplayName("SUP-007 changing a code to the CUPS of a supply of another community is refused with 400 "
            + "SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY, naming neither that community nor its owner")
    void testUpdateSupplyChangingTheCodeToADuplicateInAnotherCommunityIsRefused() throws Exception {
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        Supply otherSupply = existingSupply("ES0031300325733016FH", other.getId());
        Supply supply = existingSupply("ES0031300325733017FH0F", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String response = putSupply(authHeader, supply, "ES0031300325733016FH0F")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY"))
                .andReturn().getResponse().getContentAsString();

        for (String named : List.of(other.getName(), other.getId().toString(), otherSupply.getUser().getFullName(),
                otherSupply.getUser().getPersonalId(), "ES0031300325733016FH")) {
            Assertions.assertFalse(response.contains(named), named);
        }
        Assertions.assertEquals("ES0031300325733017FH0F",
                getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow().getCode());
    }

    @Test
    @DisplayName("SUP-007 changing a code to the other length of its own CUPS is accepted: the supply is not "
            + "compared with itself")
    void testUpdateSupplyChangingTheCodeToItsOtherLengthSucceeds() throws Exception {
        Supply supply = existingSupply("ES0031300325733018FH", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        putSupply(authHeader, supply, "ES0031300325733018FH0F")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ES0031300325733018FH0F"));
    }

    private org.springframework.test.web.servlet.ResultActions putSupply(String authHeader, Supply supply,
                                                                        String code) throws Exception {
        UpdateSupplyBody body = new UpdateSupplyBody();
        body.setCode(code);
        body.setName("Renamed");
        body.setAddress("New address");
        return mockMvc.perform(put(String.format("%s/%s", PATH, supply.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andDo(print());
    }

    /**
     * Written through the repository, which does not check the code, so that codes stored before the CUPS rules can
     * be set up too.
     */
    private Supply existingSupply(String code, UUID communityId) {
        User owner = createUserRepository.create(UserMother.randomUser());
        return createSupplyRepository.create(SupplyMother.random(owner).withCode(code).build(),
                UserId.of(owner.getId()), communityId);
    }
}
