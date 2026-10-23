package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.accept.BaseInvitationAcceptanceTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Responses scoped to a community show the member record under its own names, and user responses read by a community
 * admin read their member data from it (MEM-026, #396, #448). One person holds an account with data of its own and is
 * registered, through member registration, in two communities with a different record in each, then accepts both
 * invitations.
 */
class MemberRecordInResponsesTest extends BaseInvitationAcceptanceTest {

    private static final String CLIENT_IP = "198.51.100.7";

    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    private Community communityA;
    private Community communityB;
    private String adminOfA;
    private String adminOfB;
    private User person;
    private Map<String, Object> recordA;
    private Map<String, Object> recordB;
    private Supply supplyInA;
    private Supply supplyInB;

    @BeforeEach
    void aPersonRegisteredInTwoCommunitiesWithDifferentRecords() throws Exception {
        communityA = newCommunity();
        communityB = newCommunity();
        adminOfA = loginAsAdminOf(communityA);
        adminOfB = loginAsAdminOf(communityB);
        person = newUser();
        recordA = registerAndAccept(communityA, adminOfA, record("A", 101));
        recordB = registerAndAccept(communityB, adminOfB, record("B", 202));
        supplyInA = createSupplyRepository.create(SupplyMother.random(person).build(), UserId.of(person.getId()),
                communityA.getId());
        supplyInB = createSupplyRepository.create(SupplyMother.random(person).build(), UserId.of(person.getId()),
                communityB.getId());
    }

    // --- Community-scoped responses ---

    @Test
    @DisplayName("MEM-026 The roster shows each community's record of the person, and the account by id only")
    void theRosterShowsTheCommunitysRecord() throws Exception {
        JsonNode inA = rosterEntry(adminOfA, communityA);
        JsonNode inB = rosterEntry(adminOfB, communityB);

        assertShowsRecord(inA, recordA);
        assertShowsRecord(inB, recordB);
        assertThat(inA.get("personalId").asText()).isEqualTo(person.getPersonalId());
        assertIsReferenceToThePerson(inA.get("user"));
        assertIsReferenceToThePerson(inB.get("user"));
    }

    @Test
    @DisplayName("MEM-026 Changing the person's role answers the community's record, and the account by id only")
    void updatingTheRoleShowsTheCommunitysRecord() throws Exception {
        JsonNode updated = json(perform(adminOfA, patch("/api/v1/communities/{communityId}/memberships/{userId}",
                communityA.getId(), person.getId()), Map.of("role", "COMMUNITY_ADMIN")));

        assertShowsRecord(updated, recordA);
        assertThat(updated.get("personalId").asText()).isEqualTo(person.getPersonalId());
        assertIsReferenceToThePerson(updated.get("user"));
    }

    @Test
    @DisplayName("MEM-026 The roster and the role change report whether the caller may list the member's supplies")
    void theRosterAndTheRoleChangeReportWhetherTheMembersSuppliesMayBeListed() throws Exception {
        assertThat(rosterEntry(adminOfA, communityA).get("capabilities").get("canListSupplies").asBoolean()).isTrue();

        JsonNode updated = json(perform(adminOfA, patch("/api/v1/communities/{communityId}/memberships/{userId}",
                communityA.getId(), person.getId()), Map.of("role", "COMMUNITY_ADMIN")));

        assertThat(updated.get("capabilities").get("canListSupplies").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("MEM-026 A supply shows its owner's record in the supply's community")
    void aSupplyShowsTheOwnersRecordInItsCommunity() throws Exception {
        assertShowsOwnerRecord(json(perform(adminOfA, get("/api/v1/supplies/{supplyId}", supplyInA.getId()))),
                recordA);
        assertShowsOwnerRecord(json(perform(adminOfB, get("/api/v1/supplies/{supplyId}", supplyInB.getId()))),
                recordB);
    }

    @Test
    @DisplayName("MEM-026 Updating, enabling and disabling a supply answer the owner's record in its community")
    void writingASupplyShowsTheOwnersRecordInItsCommunity() throws Exception {
        Map<String, Object> body = Map.of("code", supplyInA.getCode(), "address", "Calle Suministro 9");
        assertShowsOwnerRecord(json(perform(adminOfA, put("/api/v1/supplies/{supplyId}", supplyInA.getId()), body)),
                recordA);
        assertShowsOwnerRecord(json(perform(adminOfA, post("/api/v1/supplies/{supplyId}/disable",
                supplyInA.getId()))), recordA);
        assertShowsOwnerRecord(json(perform(adminOfA, post("/api/v1/supplies/{supplyId}/enable",
                supplyInA.getId()))), recordA);
    }

    @Test
    @DisplayName("MEM-026 Creating a supply answers the owner's record in its community")
    void creatingASupplyShowsTheOwnersRecordInItsCommunity() throws Exception {
        Map<String, Object> body = Map.of("code", "ES" + UUID.randomUUID().toString().replace("-", "").substring(0, 18),
                "personalId", person.getPersonalId(), "address", "Calle Nueva 3", "communityId", communityB.getId());

        assertShowsOwnerRecord(json(perform(adminOfB, post("/api/v1/supplies"), body)), recordB);
    }

    @Test
    @DisplayName("MEM-026 A community's supplies show each owner's record in that community")
    void theCommunitysSuppliesShowTheOwnersRecord() throws Exception {
        JsonNode inA = json(perform(adminOfA, get("/api/v1/communities/{communityId}/supplies", communityA.getId())));
        JsonNode inB = json(perform(adminOfB, get("/api/v1/communities/{communityId}/supplies", communityB.getId())));

        assertShowsOwnerRecord(supplyIn(inA.get("items"), supplyInA), recordA);
        assertShowsOwnerRecord(supplyIn(inB.get("items"), supplyInB), recordB);
    }

    @Test
    @DisplayName("MEM-026 A user's supplies show the owner's record in each supply's community")
    void aUsersSuppliesShowTheRecordOfEachSuppliesCommunity() throws Exception {
        String adminOfBoth = loginAsAdminOfBoth();

        JsonNode forA = json(perform(adminOfA, get("/api/v1/users/{userId}/supplies", person.getId())));
        JsonNode forBoth = json(perform(adminOfBoth, get("/api/v1/users/{userId}/supplies", person.getId())));

        assertThat(forA).hasSize(1);
        assertShowsOwnerRecord(supplyIn(forA, supplyInA), recordA);
        assertThat(forBoth).hasSize(2);
        assertShowsOwnerRecord(supplyIn(forBoth, supplyInA), recordA);
        assertShowsOwnerRecord(supplyIn(forBoth, supplyInB), recordB);
    }

    @Test
    @DisplayName("MEM-026 The owner reading their own supplies sees their own record in each supply's community, and nothing of their account but its id")
    void theOwnerReadingTheirOwnSuppliesSeesOnlyTheirOwnRecords() throws Exception {
        String self = loginUser(person);

        assertShowsOwnerRecord(json(perform(self, get("/api/v1/supplies/{supplyId}", supplyInA.getId()))), recordA);
        assertShowsOwnerRecord(json(perform(self, get("/api/v1/supplies/{supplyId}", supplyInB.getId()))), recordB);
        JsonNode own = json(perform(self, get("/api/v1/users/{userId}/supplies", person.getId())));
        assertThat(own).hasSize(2);
        assertShowsOwnerRecord(supplyIn(own, supplyInA), recordA);
        assertShowsOwnerRecord(supplyIn(own, supplyInB), recordB);
    }

    @Test
    @DisplayName("MEM-026 A supply whose owner left its community has no owner record, never the account")
    void aSupplyWhoseOwnerLeftItsCommunityShowsNoMemberData() throws Exception {
        // Written directly: deleting the membership of a supply owner is refused since #412 (MEM-029), but data
        // written before it can still hold a supply whose owner left its community
        jdbcTemplate.update("DELETE FROM community_memberships WHERE user_id = ? AND community_id = ?", person.getId(),
                communityA.getId());

        JsonNode supply = json(perform(adminOfA, get("/api/v1/supplies/{supplyId}", supplyInA.getId())));

        assertIsReferenceToThePerson(supply.get("user"));
        assertThat(supply.has("ownerRecord")).isTrue();
        assertThat(supply.get("ownerRecord").isNull()).isTrue();
        assertCarriesNoPersonalId(supply);
    }

    @Test
    @DisplayName("MEM-026 A member registered without a global number shows the record's member number")
    void aMemberWithoutAGlobalNumberShowsTheRecordsNumber() throws Exception {
        Map<String, Object> body = newRecord();
        body.put("memberNumber", 42);
        UUID registeredId = registered(adminOfA, communityA.getId(), body);
        assertThat(accountRow((String) body.get("personalId")).get("number")).isNull();
        User registeredOwner = new User();
        registeredOwner.setId(registeredId);
        Supply supply = createSupplyRepository.create(SupplyMother.random(registeredOwner).build(),
                UserId.of(registeredId), communityA.getId());

        JsonNode roster = json(perform(adminOfA, get("/api/v1/communities/{communityId}/memberships",
                communityA.getId())));
        JsonNode supplies = json(perform(adminOfA, get("/api/v1/communities/{communityId}/supplies",
                communityA.getId())));

        assertThat(entryOf(roster, registeredId).get("memberNumber").asInt()).isEqualTo(42);
        assertThat(supplyIn(supplies.get("items"), supply).get("ownerRecord").get("memberNumber").asInt())
                .isEqualTo(42);
    }

    // --- User responses ---

    @Test
    @DisplayName("MEM-026 A community admin reading the user sees the record of their community")
    void aCommunityAdminReadingTheUserSeesTheirCommunitysRecord() throws Exception {
        assertShowsUserRecord(json(perform(adminOfA, get("/api/v1/users/{userId}", person.getId()))), recordA);
        assertShowsUserRecord(json(perform(adminOfB, get("/api/v1/users/{userId}", person.getId()))), recordB);
    }

    @Test
    @DisplayName("MEM-026 A community admin listing users sees the record of their community")
    void aCommunityAdminListingUsersSeesTheirCommunitysRecord() throws Exception {
        assertShowsUserRecord(userIn(json(perform(adminOfA, get("/api/v1/users?size=2000"))).get("items")), recordA);
        assertShowsUserRecord(userIn(json(perform(adminOfB, get("/api/v1/users?size=2000"))).get("items")), recordB);
    }

    @Test
    @DisplayName("MEM-026 An admin of both communities sees the record of the one with the lowest id, every time")
    void anAdminOfBothCommunitiesSeesTheRecordOfTheLowestCommunityId() throws Exception {
        String adminOfBoth = loginAsAdminOfBoth();
        Map<String, Object> lowest = communityA.getId().toString().compareTo(communityB.getId().toString()) < 0
                ? recordA : recordB;

        assertShowsUserRecord(json(perform(adminOfBoth, get("/api/v1/users/{userId}", person.getId()))), lowest);
        assertShowsUserRecord(json(perform(adminOfBoth, get("/api/v1/users/{userId}", person.getId()))), lowest);
        assertShowsUserRecord(userIn(json(perform(adminOfBoth, get("/api/v1/users?size=2000"))).get("items")), lowest);
    }

    @Test
    @DisplayName("MEM-026 The person reading their own user sees their account")
    void thePersonReadingThemselvesSeesTheirAccount() throws Exception {
        String self = loginUser(person);

        assertShowsAccount(json(perform(self, get("/api/v1/users/{userId}", person.getId()))));
    }

    @Test
    @DisplayName("MEM-026 ACC-020 The platform admin can read neither the user nor the listing, so neither the account nor a record")
    void thePlatformAdminSeesNeitherTheAccountNorARecord() throws Exception {
        String platformAdmin = loginAsPlatformAdmin();

        mockMvc.perform(get("/api/v1/users/{userId}", person.getId()).header(HttpHeaders.AUTHORIZATION, platformAdmin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users?size=2000").header(HttpHeaders.AUTHORIZATION, platformAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEM-026 A community's admin names are their member records' full names")
    void adminNamesAreTheRecordsFullNames() throws Exception {
        Map<String, Object> adminRecord = newAdminRecord();
        User admin = newUser();
        adminRecord.put("personalId", admin.getPersonalId());
        adminRecord.put("fullName", "Admin Record Name " + UUID.randomUUID());
        inviteAdmin(loginAsPlatformAdmin(), communityA.getId(), adminRecord).andExpect(status().isOk());
        acceptAsUser(loginUser(admin), tokenFrom(emailTo(awaitNextEmailsHandedOver(),
                        (String) adminRecord.get("contactEmail"))), CLIENT_IP)
                .andExpect(status().isNoContent());

        JsonNode adminNames = json(community(adminOfA, communityA.getId())).get("adminNames");

        List<String> names = new ArrayList<>();
        adminNames.forEach(name -> names.add(name.asText()));
        assertThat(names).contains((String) adminRecord.get("fullName")).doesNotContain(admin.getFullName());
    }

    // --- Fixtures ---

    private Map<String, Object> record(String community, int memberNumber) {
        Map<String, Object> body = newRecord();
        body.put("personalId", person.getPersonalId());
        body.put("fullName", "Name in " + community + " " + UUID.randomUUID());
        body.put("memberNumber", memberNumber);
        body.put("address", "Calle " + community + " " + memberNumber);
        body.put("phoneNumber", "6" + memberNumber + "000000");
        return body;
    }

    private Map<String, Object> registerAndAccept(Community community, String bearer, Map<String, Object> body)
            throws Exception {
        UUID userId = registered(bearer, community.getId(), body);
        assertThat(userId).isEqualTo(person.getId());
        invite(bearer, community.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);
        acceptAsUser(loginUser(person), token, CLIENT_IP).andExpect(status().isNoContent());
        return body;
    }

    private String loginAsAdminOfBoth() throws Exception {
        User admin = memberOf(communityA, CommunityRole.COMMUNITY_ADMIN);
        createMembershipRepository.create(communityB.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        return loginUser(admin);
    }

    // --- Requests ---

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }

    private ResultActions perform(String bearer, MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return perform(bearer, request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode rosterEntry(String bearer, Community community) throws Exception {
        return entryOf(json(perform(bearer, get("/api/v1/communities/{communityId}/memberships",
                community.getId()))), person.getId());
    }

    private static JsonNode entryOf(JsonNode roster, UUID userId) {
        for (JsonNode entry : roster) {
            if (entry.get("user").get("id").asText().equals(userId.toString())) {
                return entry;
            }
        }
        throw new AssertionError("No roster entry for user " + userId);
    }

    private static JsonNode supplyIn(JsonNode supplies, Supply supply) {
        for (JsonNode entry : supplies) {
            if (entry.get("id").asText().equals(supply.getId().toString())) {
                return entry;
            }
        }
        throw new AssertionError("No supply " + supply.getId() + " in " + supplies);
    }

    private JsonNode userIn(JsonNode users) {
        for (JsonNode entry : users) {
            if (entry.get("id").asText().equals(person.getId().toString())) {
                return entry;
            }
        }
        throw new AssertionError("No user " + person.getId() + " in the listing");
    }

    // --- Assertions ---

    /**
     * A community-scoped record: under {@code MemberResponse}'s names.
     */
    private void assertShowsRecord(JsonNode memberRecord, Map<String, Object> record) {
        assertThat(List.of(text(memberRecord.get("fullName")), text(memberRecord.get("memberNumber")),
                text(memberRecord.get("contactEmail")), text(memberRecord.get("address")),
                text(memberRecord.get("phoneNumber")))).isEqualTo(expected(record));
    }

    private void assertShowsOwnerRecord(JsonNode supply, Map<String, Object> record) {
        assertShowsRecord(supply.get("ownerRecord"), record);
        assertIsReferenceToThePerson(supply.get("user"));
        assertCarriesNoPersonalId(supply);
    }

    private void assertIsReferenceToThePerson(JsonNode user) {
        List<String> fields = new ArrayList<>();
        user.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("id");
        assertThat(user.get("id").asText()).isEqualTo(person.getId().toString());
    }

    private static void assertCarriesNoPersonalId(JsonNode supply) {
        assertThat(supply.findValues("personalId")).isEmpty();
    }

    private static List<String> expected(Map<String, Object> record) {
        return List.of(
                String.valueOf(record.get("fullName")),
                String.valueOf(record.get("memberNumber")),
                String.valueOf(record.get("contactEmail")),
                String.valueOf(record.get("address")),
                String.valueOf(record.get("phoneNumber")));
    }

    /**
     * A user response: the member data under the user's names.
     */
    private void assertShowsUserRecord(JsonNode user, Map<String, Object> record) {
        assertThat(memberData(user)).isEqualTo(List.of(
                String.valueOf(record.get("fullName")),
                String.valueOf(record.get("memberNumber")),
                String.valueOf(record.get("contactEmail")),
                String.valueOf(record.get("address")),
                String.valueOf(record.get("phoneNumber"))));
    }

    private void assertShowsAccount(JsonNode user) {
        assertThat(user.get("id").asText()).isEqualTo(person.getId().toString());
        assertThat(memberData(user)).isEqualTo(List.of(
                String.valueOf(person.getFullName()),
                String.valueOf(person.getNumber()),
                String.valueOf(person.getEmail()),
                String.valueOf(person.getAddress()),
                String.valueOf(person.getPhoneNumber())));
    }

    private static List<String> memberData(JsonNode user) {
        return List.of(text(user.get("fullName")), text(user.get("number")), text(user.get("email")),
                text(user.get("address")), text(user.get("phoneNumber")));
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? "null" : value.asText();
    }
}
