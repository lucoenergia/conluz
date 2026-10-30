package org.lucoenergia.conluz.infrastructure.admin.supply.create;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.get.GetCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.community.CommunityMother.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CreateSupplyControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/supplies";

    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private GetCommunityRepository getCommunityRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private CommunityMembershipJpaRepository communityMembershipJpaRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    @Test
    void testCreateSupplyWithoutName() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String userPersonalId = "54889216G";
        User user = UserMother.randomUser();
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);

        String body = String.format("""
                {
                  "code": "ES0033333333333333AA0A",
                  "communityId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                  "personalId": "%s",
                  "address": "Fake Street 123",
                  "addressRef": "4ASDF654ASDF89ASD"
                }
        """, userPersonalId);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value("ES0033333333333333AA0A"))
                .andExpect(jsonPath("$.address").value("Fake Street 123"))
                .andExpect(jsonPath("$.addressRef").value("4ASDF654ASDF89ASD"))
                .andExpect(jsonPath("$.name").isEmpty())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.personalId").doesNotExist())
                .andExpect(jsonPath("$.user.enabled").doesNotExist())
                .andExpect(jsonPath("$.ownerRecord.memberNumber").value(user.getNumber()))
                .andExpect(jsonPath("$.ownerRecord.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.ownerRecord.address").value(user.getAddress()))
                .andExpect(jsonPath("$.ownerRecord.contactEmail").value(user.getEmail()))
                .andExpect(jsonPath("$.ownerRecord.phoneNumber").value(user.getPhoneNumber()));

        Assertions.assertTrue(supplyRepository.findByCode("ES0033333333333333AA0A").isPresent());
    }

    /**
     * addressRef is absent from the schema's requiredProperties and its column is nullable, so the
     * server must accept a body without it rather than contradict its own published contract.
     */
    @Test
    void testCreateSupplyWithoutAddressRef() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String userPersonalId = "54889216G";
        User user = UserMother.randomUser();
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);

        String body = String.format("""
                {
                  "code": "ES0066666666666666AA0A",
                  "communityId": "%s",
                  "personalId": "%s",
                  "address": "Fake Street 123"
                }
        """, DEFAULT_COMMUNITY_ID, userPersonalId);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ES0066666666666666AA0A"))
                .andExpect(jsonPath("$.addressRef").isEmpty());
    }

    @Test
    void testCreateSupplyReportsTheCommunityItWasCreatedIn() throws Exception {

        Community community = createCommunityRepository.create(CommunityMother.random().build());

        String authHeader = loginAsCommunityAdmin(community.getId());

        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);

        String body = String.format("""
                {
                  "code": "ES0055555555555555AA0A",
                  "communityId": "%s",
                  "personalId": "%s",
                  "address": "Fake Street 123",
                  "addressRef": "4ASDF654ASDF89ASD"
                }
        """, community.getId(), user.getPersonalId());

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.id").value(community.getId().toString()))
                .andExpect(jsonPath("$.community.name").value(community.getName()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678a", " 12345678 A ", "12345678-A", "12.345.678-A"})
    void testCreateSupplyResolvesTheOwnerFromATypingVariantOfTheirPersonalId(String variant) throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User owner = UserMother.randomUserWithPersonalId("12345678A");
        createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, owner.getId(), CommunityRole.COMMUNITY_MEMBER);

        String body = objectMapper.writeValueAsString(Map.of(
                "code", "ES0033333333333333EE0E",
                "communityId", DEFAULT_COMMUNITY_ID.toString(),
                "personalId", variant,
                "address", "Fake Street 456"));

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.user.personalId").doesNotExist());

        UUID storedOwnerId = supplyRepository.findByCode("ES0033333333333333EE0E").orElseThrow().getUser().getId();
        Assertions.assertEquals(owner.getId(), storedOwnerId);
    }

    @Test
    void testCreateSupplyWithName() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String userPersonalId = "54889216G";
        User user = UserMother.randomUser();
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);

        String body = String.format("""
                {
                  "code": "ES0033333333333333BB0B",
                  "communityId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
                  "personalId": "%s",
                  "address": "Fake Street 456",
                  "addressRef": "4ASDF654ASDF89ASD",
                  "name": "Test Supply Name"
                }
        """, userPersonalId);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.code").value("ES0033333333333333BB0B"))
                .andExpect(jsonPath("$.address").value("Fake Street 456"))
                .andExpect(jsonPath("$.addressRef").value("4ASDF654ASDF89ASD"))
                .andExpect(jsonPath("$.name").value("Test Supply Name")) // Name is explicitly provided
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.user.personalId").doesNotExist())
                .andExpect(jsonPath("$.user.enabled").doesNotExist())
                .andExpect(jsonPath("$.ownerRecord.memberNumber").value(user.getNumber()))
                .andExpect(jsonPath("$.ownerRecord.fullName").value(user.getFullName()))
                .andExpect(jsonPath("$.ownerRecord.address").value(user.getAddress()))
                .andExpect(jsonPath("$.ownerRecord.contactEmail").value(user.getEmail()))
                .andExpect(jsonPath("$.ownerRecord.phoneNumber").value(user.getPhoneNumber()));

        Assertions.assertTrue(supplyRepository.findByCode("ES0033333333333333BB0B").isPresent());
    }

    @Test
    void testWithDuplicatedSupply() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User user = UserMother.randomUser();
        createUserRepository.create(user);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supply = SupplyMother.random().build();
        createSupplyService.create(supply, UserPersonalId.of(user.getPersonalId()), DEFAULT_COMMUNITY_ID);

        String body = String.format("""
                {
                  "code": "%s",
                  "personalId": "%s",
                  "address": "%s",
                  "addressRef": "%s",
                  "communityId": "%s"
                }
        """, supply.getCode(), user.getPersonalId(), supply.getAddress(), supply.getAddressRef(),
                DEFAULT_COMMUNITY_ID);

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

    @ParameterizedTest
    @MethodSource("getBodyWithMissingRequiredFields")
    void testMissingRequiredFields(String body) throws Exception {

        User user = UserMother.randomUser();
        user.setPersonalId("54889216G");
        createUserRepository.create(user);

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

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
        return List.of("""
                        {
                          "personalId": "54889216G",
                          "address": "Fake Street 456",
                          "addressRef": "4ASDF654ASDF89ASD"
                        }
                """,
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "address": "Fake Street 456",
                          "addressRef": "4ASDF654ASDF89ASD"
                        }
                """,
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "personalId": "54889216G",
                          "addressRef": "4ASDF654ASDF89ASD"
                        }
                """,
                // Missing communityId (required) -> 400
                """
                        {
                          "code": "ES0033333333333333BB0B",
                          "personalId": "54889216G",
                          "address": "Fake Street 456",
                          "addressRef": "4ASDF654ASDF89ASD"
                        }
                """
                );
    }

    @Test
    void testWithCommunityTheCallerCannotManageReturnsNotFound() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        String userPersonalId = "54889216G";
        User user = UserMother.randomUser();
        user.setPersonalId(userPersonalId);
        createUserRepository.create(user);

        // A community the caller does not administer -> 404 (existence is not leaked)
        String body = String.format("""
                {
                  "code": "ES0033333333333333DD0D",
                  "communityId": "%s",
                  "personalId": "%s",
                  "address": "Fake Street 123",
                  "addressRef": "4ASDF654ASDF89ASD"
                }
        """, java.util.UUID.randomUUID(), userPersonalId);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    /**
     * #341: an owner who is not a member of the supply's community gets exactly the answer an unknown
     * personalId gets, so a community admin cannot attach another community's member to a supply,
     * nor learn that their personalId is registered on the platform.
     */
    @Test
    @DisplayName("SUP-002 an owner who is not a member of the community gets exactly the answer of an unknown "
            + "personalId, 404 SUPPLY_OWNER_NOT_FOUND, naming neither")
    void testCreateSupplyForAMemberOfAnotherCommunityAnswersExactlyLikeAnUnknownPersonalId() throws Exception {

        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        User memberOfB = UserMother.randomUser();
        createUserRepository.create(memberOfB);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, memberOfB.getId(), CommunityRole.COMMUNITY_MEMBER);
        String unknownPersonalId = UserMother.randomUser().getPersonalId();

        ObjectNode memberOfBResponse = postSupplyExpectingNotFound(authHeader, communityA.getId(), "ES0033333333333333GG0G",
                memberOfB.getPersonalId());
        ObjectNode unknownResponse = postSupplyExpectingNotFound(authHeader, communityA.getId(), "ES0033333333333333HH0H",
                unknownPersonalId);

        // Everything but the trace id and the timestamp must match, and neither answer names the personalId sent
        Assertions.assertEquals("SUPPLY_OWNER_NOT_FOUND", unknownResponse.at("/errors/0/code").asText());
        Assertions.assertFalse(memberOfBResponse.toString().contains(memberOfB.getPersonalId()));
        Assertions.assertFalse(unknownResponse.toString().contains(unknownPersonalId));
        Assertions.assertEquals(
                withoutVaryingFields(unknownResponse, unknownPersonalId),
                withoutVaryingFields(memberOfBResponse, memberOfB.getPersonalId()));

        Assertions.assertTrue(getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333GG0G")).isEmpty());
        Assertions.assertTrue(getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333HH0H")).isEmpty());
    }

    @Test
    void testCreateSupplyForAMemberOfBothCommunitiesCreatesItInTheTargetOne() throws Exception {

        Community communityA = createCommunityRepository.create(CommunityMother.random().build());
        String authHeader = loginAsCommunityAdmin(communityA.getId());

        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipRepository.create(communityA.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(communityA.getId(), "ES0033333333333333JJ0J", owner.getPersonalId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.community.id").value(communityA.getId().toString()));

        Supply stored = getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333JJ0J")).orElseThrow();
        Assertions.assertEquals(owner.getId(), stored.getUser().getId());
        Assertions.assertEquals(communityA.getId(), stored.getCommunity().getId());
    }

    /**
     * Disabling a membership governs platform access, not supply ownership: a member whose
     * membership is disabled can still be registered as the owner of a supply in that community.
     */
    @Test
    void testCreateSupplyForAMemberWhoseMembershipIsDisabledCreatesIt() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        // No endpoint disables a membership yet, so the state is set directly.
        CommunityMembershipEntity membership = communityMembershipJpaRepository
                .findByUserIdAndCommunityId(owner.getId(), DEFAULT_COMMUNITY_ID).orElseThrow();
        membership.setEnabled(false);
        communityMembershipJpaRepository.saveAndFlush(membership);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, "ES0033333333333333KK0K", owner.getPersonalId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(owner.getId().toString()));

        Supply stored = getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333KK0K")).orElseThrow();
        Assertions.assertEquals(owner.getId(), stored.getUser().getId());
    }

    @Test
    @DisplayName("MEM-004 a supply can be created for a member whose membership is still pending")
    void testCreateSupplyForAMemberWhoseMembershipIsPendingCreatesIt() throws Exception {

        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        createMembershipRepository.create(DEFAULT_COMMUNITY_ID, owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(communityMembershipJpaRepository, owner.getId(), DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, "ES0033333333333333PP0P", owner.getPersonalId())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(owner.getId().toString()));

        Supply stored = getSupplyRepository.findByCode(SupplyCode.of("ES0033333333333333PP0P")).orElseThrow();
        Assertions.assertEquals(owner.getId(), stored.getUser().getId());
    }

    private ObjectNode postSupplyExpectingNotFound(String authHeader, UUID communityId, String code,
                                                   String personalId) throws Exception {
        String response = mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(communityId, code, personalId)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return (ObjectNode) objectMapper.readTree(response);
    }

    /**
     * The response without its trace id and timestamp, and with every occurrence of the personalId
     * that was sent (it is quoted in the message and in each error entry) replaced by a placeholder.
     */
    private JsonNode withoutVaryingFields(ObjectNode response, String personalId) throws Exception {
        ObjectNode copy = response.deepCopy();
        copy.remove("traceId");
        copy.remove("timestamp");
        return objectMapper.readTree(objectMapper.writeValueAsString(copy).replace(personalId, "{personalId}"));
    }

    private String supplyBody(UUID communityId, String code, String personalId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "code", code,
                "communityId", communityId.toString(),
                "personalId", personalId,
                "address", "Fake Street 789"));
    }

    @Test
    void testWithoutBody() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);

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
    void testAuthenticatedUserWithoutAdminRoleGetsNotFound() throws Exception {

        String authHeader = loginAsPartner();

        String userPersonalId = "54889216G";
        String body = String.format("""
                {
                  "code": "ES0033333333333333AA0A",
                  "communityId": "%s",
                  "personalId": "%s",
                  "address": "Fake Street 123",
                  "addressRef": "4ASDF654ASDF89ASD"
                }
        """, DEFAULT_COMMUNITY_ID, userPersonalId);

        mockMvc.perform(post(URL)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @ParameterizedTest
    @DisplayName("SUP-006 a code that is not a CUPS once upper-cased is refused with 400 SUPPLY_CUPS_INVALID, "
            + "naming no code, and nothing is created")
    @ValueSource(strings = {"ES0031300325733001F", "ES0031300325733001FH0", "ES0031300325733001FH0FX",
            "ES00313003257330 1FH0F", "ES0031300325733001\tH"})
    void testCreateSupplyWithACodeThatIsNotACupsIsRefused(String code) throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User owner = memberOf(DEFAULT_COMMUNITY_ID);

        String response = mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, code, owner.getPersonalId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_CUPS_INVALID"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(response.contains(code));
        Assertions.assertTrue(getSupplyRepository.findAllByCommunityId(DEFAULT_COMMUNITY_ID).stream()
                .noneMatch(supply -> supply.getUser().getId().equals(owner.getId())));
    }

    @Test
    @DisplayName("SUP-006 a lower-case code is stored upper-cased")
    void testCreateSupplyStoresTheCodeUpperCased() throws Exception {
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User owner = memberOf(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, "es0031300325733005fh", owner.getPersonalId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ES0031300325733005FH"));

        Assertions.assertTrue(supplyRepository.findByCode("ES0031300325733005FH").isPresent());
        Assertions.assertTrue(supplyRepository.findByCode("es0031300325733005fh").isEmpty());
    }

    @ParameterizedTest
    @DisplayName("SUP-007 a CUPS another supply of the community has, in either length and in any case, is refused "
            + "with 400 SUPPLY_ALREADY_EXISTS, naming no code, and nothing is created")
    @CsvSource({
            "ES0031300325733003FH, ES0031300325733003FH0F",
            "ES0031300325733003FH0F, ES0031300325733003FH",
            "ES0031300325733003FH0F, es0031300325733003fh0f",
            "es0031300325733003fh, ES0031300325733003FH0F",
            "ES0031300325733003FH0F, ES0031300325733003FH0F"
    })
    void testCreateSupplyWithTheCupsOfASupplyOfTheCommunityIsRefused(String stored, String submitted)
            throws Exception {
        existingSupply(stored, DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User owner = memberOf(DEFAULT_COMMUNITY_ID);

        String response = mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, submitted, owner.getPersonalId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(response.toUpperCase(Locale.ROOT).contains(stored.toUpperCase(Locale.ROOT)));
        Assertions.assertFalse(response.toUpperCase(Locale.ROOT).contains(submitted.toUpperCase(Locale.ROOT)));
        Assertions.assertTrue(getSupplyRepository.findAllByCommunityId(DEFAULT_COMMUNITY_ID).stream()
                .noneMatch(supply -> supply.getUser().getId().equals(owner.getId())));
    }

    @Test
    @DisplayName("SUP-007 a CUPS a supply of another community has is refused with 400 "
            + "SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY, naming neither that community nor its owner")
    void testCreateSupplyWithTheCupsOfASupplyOfAnotherCommunityIsRefused() throws Exception {
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        User otherOwner = existingSupply("ES0031300325733006FH0F", other.getId());
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User owner = memberOf(DEFAULT_COMMUNITY_ID);

        String response = mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, "ES0031300325733006FH", owner.getPersonalId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SUPPLY_REGISTERED_IN_ANOTHER_COMMUNITY"))
                .andReturn().getResponse().getContentAsString();

        for (String named : List.of(other.getName(), other.getId().toString(), otherOwner.getFullName(),
                otherOwner.getPersonalId(), otherOwner.getId().toString(), "ES0031300325733006FH")) {
            Assertions.assertFalse(response.contains(named), named);
        }
        Assertions.assertTrue(supplyRepository.findByCode("ES0031300325733006FH").isEmpty());
    }

    @Test
    @DisplayName("SUP-007 a 22-character CUPS that does not end in 0F is not the same CUPS as its first 20 "
            + "characters")
    void testCreateSupplyWithAFullCupsNotEndingIn0FDoesNotMatchItsPrefix() throws Exception {
        existingSupply("ES0031300325733004FH", DEFAULT_COMMUNITY_ID);
        String authHeader = loginAsCommunityAdmin(DEFAULT_COMMUNITY_ID);
        User owner = memberOf(DEFAULT_COMMUNITY_ID);

        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplyBody(DEFAULT_COMMUNITY_ID, "ES0031300325733004FH1F", owner.getPersonalId())))
                .andExpect(status().isOk());
    }

    private User memberOf(UUID communityId) {
        User user = createUserRepository.create(UserMother.randomUser());
        createMembershipRepository.create(communityId, user.getId(), CommunityRole.COMMUNITY_MEMBER);
        return user;
    }

    /**
     * Written through the repository, which does not check the code, so that codes stored before the CUPS rules can
     * be set up too. Returns its owner.
     */
    private User existingSupply(String code, UUID communityId) {
        User owner = memberOf(communityId);
        createSupplyRepository.create(SupplyMother.random(owner).withCode(code).build(), UserId.of(owner.getId()),
                communityId);
        return owner;
    }
}
