package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A community admin editing the member records of their community (MEM-028, #416): only the record is written, never
 * the account, the person's other memberships, or the membership's role and state. Changing the contact email of a
 * pending membership revokes its invitation link (#482).
 */
class UpdateMemberRecordControllerTest extends BaseInvitationAcceptanceTest {

    private static final String CLIENT_IP = "198.51.100.9";
    private static final List<String> RECORD_COLUMNS =
            List.of("full_name", "member_number", "contact_email", "address", "phone_number");

    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    // --- AC1: any state, only the record ---

    @Test
    @DisplayName("MEM-028 an admin edits the record of a pending, an active and a disabled membership; the "
            + "membership's state and invitation and the account are not written")
    void theRecordOfAPendingAnActiveAndADisabledMembership_isEdited_andTheAccountIsNot() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        String pending = (String) registeredRecord(admin, community).get("personalId");
        String active = memberOf(community, CommunityRole.COMMUNITY_MEMBER).getPersonalId();
        User disabledMember = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/{userId}/disable", community.getId(),
                disabledMember.getId()).header(HttpHeaders.AUTHORIZATION, admin)).andExpect(status().isOk());
        String disabled = disabledMember.getPersonalId();

        for (String personalId : List.of(pending, active, disabled)) {
            UUID userId = userIdOf(personalId);
            Map<String, Object> accountBefore = accountRow(personalId);
            Map<String, Object> membershipBefore = membershipRow(personalId, community.getId());
            Map<String, Object> edit = newEdit();

            String response = updateRecord(admin, community.getId(), userId, edit)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.id").value(userId.toString()))
                    .andExpect(jsonPath("$.community.id").value(community.getId().toString()))
                    .andExpect(jsonPath("$.personalId").value(personalId))
                    .andExpect(jsonPath("$.role").value("COMMUNITY_MEMBER"))
                    .andExpect(jsonPath("$.enabled").value(membershipBefore.get("enabled")))
                    .andExpect(jsonPath("$.capabilities.canEditRecord").value(true))
                    .andReturn().getResponse().getContentAsString();

            assertResponseShows(objectMapper.readTree(response), edit);
            assertRowShows(membershipRow(personalId, community.getId()), edit);
            assertThat(withoutRecord(membershipRow(personalId, community.getId())))
                    .isEqualTo(withoutRecord(membershipBefore));
            assertThat(accountRow(personalId)).isEqualTo(accountBefore);
            assertNoSecretIn(response.replace((String) edit.get("contactEmail"), ""));
        }
    }

    // --- AC2: one community's record ---

    @Test
    @DisplayName("MEM-028 editing a person's record in one community leaves their other record and the account "
            + "unchanged, and each community's roster and supplies show its own record")
    void editingOneCommunitysRecord_leavesTheOtherAndTheAccountUnchanged() throws Exception {
        Community communityA = newCommunity();
        Community communityB = newCommunity();
        String adminOfA = loginAsAdminOf(communityA);
        String adminOfB = loginAsAdminOf(communityB);
        User person = newUser();
        registerAndAccept(person, communityA, adminOfA);
        Map<String, Object> recordB = registerAndAccept(person, communityB, adminOfB);
        Supply supplyInA = createSupplyRepository.create(SupplyMother.random(person).build(),
                UserId.of(person.getId()), communityA.getId());
        Supply supplyInB = createSupplyRepository.create(SupplyMother.random(person).build(),
                UserId.of(person.getId()), communityB.getId());
        Map<String, Object> accountBefore = accountRow(person.getPersonalId());
        Map<String, Object> recordBBefore = membershipRow(person.getPersonalId(), communityB.getId());
        Map<String, Object> edit = newEdit();

        updateRecord(adminOfA, communityA.getId(), person.getId(), edit).andExpect(status().isOk());

        assertRowShows(membershipRow(person.getPersonalId(), communityA.getId()), edit);
        assertThat(membershipRow(person.getPersonalId(), communityB.getId())).isEqualTo(recordBBefore);
        assertThat(accountRow(person.getPersonalId())).isEqualTo(accountBefore);
        assertShowsMemberData(rosterEntry(adminOfA, communityA, person.getId()), edit);
        assertShowsMemberData(supply(adminOfA, supplyInA), edit);
        assertShowsMemberData(rosterEntry(adminOfB, communityB, person.getId()), recordB);
        assertShowsMemberData(supply(adminOfB, supplyInB), recordB);
    }

    // --- AC3: a whole record, blank values are none ---

    @Test
    @DisplayName("MEM-028 an optional value that is omitted or null is cleared")
    void omittedOrNullOptionalValues_areCleared() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        Map<String, Object> registered = registeredRecord(admin, community);
        String personalId = (String) registered.get("personalId");
        UUID userId = userIdOf(personalId);

        updateRecord(admin, community.getId(), userId, Map.of("fullName", "Only A Name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberNumber").isEmpty())
                .andExpect(jsonPath("$.contactEmail").isEmpty())
                .andExpect(jsonPath("$.address").isEmpty())
                .andExpect(jsonPath("$.phoneNumber").isEmpty());
        assertRecordIsEmptyBut("Only A Name", membershipRow(personalId, community.getId()));

        updateRecord(admin, community.getId(), userId, newEdit()).andExpect(status().isOk());
        Map<String, Object> nulls = new HashMap<>();
        nulls.put("fullName", "Another Name");
        nulls.put("memberNumber", null);
        nulls.put("contactEmail", null);
        nulls.put("address", null);
        nulls.put("phoneNumber", null);
        updateRecord(admin, community.getId(), userId, nulls).andExpect(status().isOk());
        assertRecordIsEmptyBut("Another Name", membershipRow(personalId, community.getId()));
    }

    @Test
    @DisplayName("MEM-028 an empty or blank contact email, address or phone number is stored as none, and nothing "
            + "is trimmed")
    void emptyOrBlankOptionalValues_areStoredAsNone() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        String personalId = (String) registeredRecord(admin, community).get("personalId");
        UUID userId = userIdOf(personalId);

        for (String blank : List.of("", "   ", " \t ")) {
            updateRecord(admin, community.getId(), userId, newEdit()).andExpect(status().isOk());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fullName", " Spaced Name ");
            body.put("contactEmail", blank);
            body.put("address", blank);
            body.put("phoneNumber", blank);

            updateRecord(admin, community.getId(), userId, body)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contactEmail").isEmpty());

            assertRecordIsEmptyBut(" Spaced Name ", membershipRow(personalId, community.getId()));
        }
    }

    @Test
    @DisplayName("MEM-028 a blank or missing full name, a negative member number or an invalid contact email is "
            + "refused with 400, and nothing is written")
    void anInvalidRecord_isABadRequest_andNothingIsWritten() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        String personalId = (String) registeredRecord(admin, community).get("personalId");
        UUID userId = userIdOf(personalId);
        Map<String, Object> before = membershipRow(personalId, community.getId());

        Map<String, Object> blankFullName = newEdit();
        blankFullName.put("fullName", " ");
        Map<String, Object> withoutFullName = newEdit();
        withoutFullName.remove("fullName");
        Map<String, Object> negativeNumber = newEdit();
        negativeNumber.put("memberNumber", -1);
        Map<String, Object> invalidEmail = newEdit();
        invalidEmail.put("contactEmail", "not an email");

        for (Map<String, Object> body : List.of(blankFullName, withoutFullName, negativeNumber, invalidEmail)) {
            String response = updateRecord(admin, community.getId(), userId, body)
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            assertNoSecretIn(response);
        }

        assertThat(membershipRow(personalId, community.getId())).isEqualTo(before);
    }

    // --- AC4: an admin keeps a contact email ---

    @Test
    @DisplayName("MEM-028 an admin's record cannot lose its contact email: 400 MEMBERSHIP_WITHOUT_CONTACT_EMAIL and "
            + "nothing is written; with a new email it is written")
    void anAdminsRecordWithoutAContactEmail_isRefused() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        User otherAdmin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        Map<String, Object> before = membershipRow(otherAdmin.getPersonalId(), community.getId());

        for (String noEmail : new String[]{null, "", "  "}) {
            Map<String, Object> edit = newEdit();
            edit.put("contactEmail", noEmail);
            String response = updateRecord(admin, community.getId(), otherAdmin.getId(), edit)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_WITHOUT_CONTACT_EMAIL"))
                    .andReturn().getResponse().getContentAsString();
            assertNoSecretIn(response);
        }
        assertThat(membershipRow(otherAdmin.getPersonalId(), community.getId())).isEqualTo(before);

        Map<String, Object> edit = newEdit();
        updateRecord(admin, community.getId(), otherAdmin.getId(), edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMMUNITY_ADMIN"));
        assertRowShows(membershipRow(otherAdmin.getPersonalId(), community.getId()), edit);
    }

    @Test
    @DisplayName("MEM-028 an admin edits their own record, which keeps its contact email")
    void anAdminEditsTheirOwnRecord() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        String bearer = loginUser(admin);
        Map<String, Object> accountBefore = accountRow(admin.getPersonalId());

        Map<String, Object> withoutEmail = newEdit();
        withoutEmail.remove("contactEmail");
        updateRecord(bearer, community.getId(), admin.getId(), withoutEmail)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_WITHOUT_CONTACT_EMAIL"));

        Map<String, Object> edit = newEdit();
        updateRecord(bearer, community.getId(), admin.getId(), edit).andExpect(status().isOk());
        assertRowShows(membershipRow(admin.getPersonalId(), community.getId()), edit);
        assertThat(accountRow(admin.getPersonalId())).isEqualTo(accountBefore);
    }

    // --- AC5: only an active admin of the community ---

    @Test
    @DisplayName("MEM-028 a platform admin and a member are refused with 403, and nothing is written")
    void aPlatformAdminAndAMember_areForbidden() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Map<String, Object> before = membershipRow(member.getPersonalId(), community.getId());

        for (String bearer : List.of(loginAsPlatformAdmin(), loginAsMemberOf(community), loginUser(member))) {
            String response = updateRecord(bearer, community.getId(), member.getId(), newEdit())
                    .andExpect(status().isForbidden())
                    .andReturn().getResponse().getContentAsString();
            assertNoSecretIn(response);
        }

        assertThat(membershipRow(member.getPersonalId(), community.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("MEM-028 a pending admin, a disabled admin and an admin of another community are answered 404, "
            + "and nothing is written")
    void anAdminWhoCannotSeeTheCommunity_isNotFound() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Map<String, Object> before = membershipRow(member.getPersonalId(), community.getId());

        for (String bearer : List.of(loginAsPendingAdminOf(community), loginAsDisabledAdminOf(community),
                loginAsAdminOf(newCommunity()))) {
            String response = updateRecord(bearer, community.getId(), member.getId(), newEdit())
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            assertNoSecretIn(response);
        }

        assertThat(membershipRow(member.getPersonalId(), community.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("MEM-028 an unknown community, an unknown user and a member of another community are answered "
            + "404, and nothing is written")
    void anUnknownCommunityOrMembership_isNotFound() throws Exception {
        Community community = newCommunity();
        Community other = newCommunity();
        User memberOfOther = memberOf(other, CommunityRole.COMMUNITY_MEMBER);
        String admin = loginAsAdminOf(community);
        Map<String, Object> before = membershipRow(memberOfOther.getPersonalId(), other.getId());

        updateRecord(admin, UUID.randomUUID(), memberOfOther.getId(), newEdit()).andExpect(status().isNotFound());
        updateRecord(admin, community.getId(), UUID.randomUUID(), newEdit()).andExpect(status().isNotFound());
        String response = updateRecord(admin, community.getId(), memberOfOther.getId(), newEdit())
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertNoSecretIn(response);
        assertThat(membershipRow(memberOfOther.getPersonalId(), other.getId())).isEqualTo(before);
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(put("/api/v1/communities/{communityId}/memberships/{userId}/record", community.getId(),
                        member.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newEdit())))
                .andExpect(status().isUnauthorized());
    }

    // --- Invitations ---

    @Test
    @DisplayName("MEM-028 after the contact email changes, the link already sent stops working, and a resend goes "
            + "to the new address only")
    void aResendGoesToTheNewContactEmail() throws Exception {
        InvitedMember member = invitedMember();
        Map<String, Object> edit = newEdit();

        updateRecord(member.bearer(), member.community().getId(), member.userId(), edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(linkWorks(member.token())).isFalse();
        resend(member).andExpect(status().isOk());
        MimeMessage resent = awaitNextEmails(1)[0];
        assertThat(recipientsOf(resent)).containsExactly((String) edit.get("contactEmail"));
        assertThat(linkWorks(tokenFrom(resent))).isTrue();
        assertThat(linkWorks(member.token())).isFalse();
    }

    @Test
    @DisplayName("MEM-028 a member registered without a contact email is first invited at the one added to the "
            + "record")
    void theFirstInvitationGoesToTheNewContactEmail() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        body.remove("contactEmail");
        UUID userId = registered(admin, community.getId(), body);
        Map<String, Object> edit = newEdit();

        updateRecord(admin, community.getId(), userId, edit).andExpect(status().isOk());
        invite(admin, community.getId(), userId).andExpect(status().isOk());

        assertThat(recipientsOf(awaitNextEmails(1)[0])).containsExactly((String) edit.get("contactEmail"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CommunityRole.class)
    @DisplayName("MEM-028 changing a pending membership's contact email revokes its link: inspecting and accepting "
            + "it fail as invalid, and a resend reaches the new address")
    void aContactEmailChange_revokesAPendingMembershipsLink(CommunityRole role) throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        Map<String, Object> record = registeredRecord(admin, community);
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        UUID userId = userIdOf((String) record.get("personalId"));
        if (role == CommunityRole.COMMUNITY_ADMIN) {
            promote(admin, community, userId);
        }
        invite(admin, community.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(emailTo(awaitNextEmailsHandedOver(), (String) record.get("contactEmail")));
        Map<String, Object> edit = newEdit();
        remember((String) edit.get("phoneNumber"));

        updateRecord(admin, community.getId(), userId, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name()))
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(linkWorks(token)).isFalse();
        inspect(token, CLIENT_IP).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));
        accept(token, newPassword(), CLIENT_IP).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_INVITATION_TOKEN_INVALID"));
        assertThat(acceptedAt((String) record.get("personalId"), community.getId())).isNull();

        resend(admin, community.getId(), userId).andExpect(status().isOk());
        MimeMessage resent = emailTo(awaitNextEmailsHandedOver(), (String) edit.get("contactEmail"));
        String newToken = tokenFrom(resent);
        assertThat(linkWorks(newToken)).isTrue();
        inspect(newToken, CLIENT_IP).andExpect(status().isOk());
    }

    /**
     * An edit of a pending membership that keeps its contact email, as a mailbox.
     */
    private enum KeptEmail {
        SAME_EMAIL(edit -> {
        }),
        EMAIL_IN_ANOTHER_CASE(edit -> edit.put("contactEmail",
                ((String) edit.get("contactEmail")).toUpperCase(Locale.ROOT))),
        ONLY_THE_NAME(edit -> edit.put("fullName", "Another Name " + UUID.randomUUID())),
        ONLY_THE_PHONE(edit -> edit.put("phoneNumber", "688888888"));

        private final Consumer<Map<String, Object>> change;

        KeptEmail(Consumer<Map<String, Object>> change) {
            this.change = change;
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(KeptEmail.class)
    @DisplayName("MEM-028 an edit that keeps a pending membership's contact email, or changes only its case, keeps "
            + "the link")
    void anEditKeepingTheContactEmail_keepsTheLink(KeptEmail kept) throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        Map<String, Object> record = registeredRecord(admin, community);
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        UUID userId = userIdOf((String) record.get("personalId"));
        invite(admin, community.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);
        Map<String, Object> edit = new LinkedHashMap<>(record);
        edit.remove("personalId");
        kept.change.accept(edit);
        remember((String) edit.get("contactEmail"));
        remember((String) edit.get("phoneNumber"));

        updateRecord(admin, community.getId(), userId, edit)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value(edit.get("contactEmail")))
                .andExpect(jsonPath("$.invitationExpiresAt").isNotEmpty());

        assertThat(linkWorks(token)).isTrue();
        inspect(token, CLIENT_IP).andExpect(status().isOk());
    }

    @Test
    @DisplayName("MEM-028 changing an accepted membership's contact email revokes nothing")
    void aContactEmailChangeOfAnAcceptedMembership_revokesNothing() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        User person = newUser();
        registerAndAccept(person, community, admin);
        List<Map<String, Object>> tokensBefore = tokenRows(person.getPersonalId());

        updateRecord(admin, community.getId(), person.getId(), newEdit())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationExpiresAt").isEmpty());

        assertThat(tokenRows(person.getPersonalId())).isEqualTo(tokensBefore);
    }

    // --- Logging ---

    @Test
    @DisplayName("MEM-028 one line is logged per change, with the user, the community and whether a link was revoked")
    void oneLineIsLogged() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        String admin = loginAsAdminOf(community);
        logs.clear();

        updateRecord(admin, community.getId(), member.getId(), newEdit()).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member record"))
                .extracting(event -> event.getFormattedMessage())
                .containsExactly("Member record updated: userId=" + member.getId() + ", communityId="
                        + community.getId() + ", invitationRevoked=false");
    }

    @Test
    @DisplayName("MEM-028 the line logged for a change of a pending membership's contact email says its link was "
            + "revoked")
    void theLoggedLine_saysTheLinkWasRevoked() throws Exception {
        Community community = newCommunity();
        String admin = loginAsAdminOf(community);
        Map<String, Object> record = registeredRecord(admin, community);
        remember((String) record.get("personalId"));
        remember((String) record.get("phoneNumber"));
        UUID userId = userIdOf((String) record.get("personalId"));
        invite(admin, community.getId(), userId).andExpect(status().isOk());
        awaitNextEmailsHandedOver();
        Map<String, Object> edit = newEdit();
        remember((String) edit.get("phoneNumber"));
        logs.clear();

        updateRecord(admin, community.getId(), userId, edit).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member record"))
                .extracting(event -> event.getFormattedMessage())
                .containsExactly("Member record updated: userId=" + userId + ", communityId=" + community.getId()
                        + ", invitationRevoked=true");
    }

    // --- Fixtures ---

    /**
     * A whole record with every field filled, and a fresh contact email.
     */
    private Map<String, Object> newEdit() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Edited Name " + UUID.randomUUID());
        body.put("memberNumber", 7);
        body.put("contactEmail", newEmail());
        body.put("address", "Calle Nueva 2, Luco");
        body.put("phoneNumber", "699999999");
        return body;
    }

    private Map<String, Object> registeredRecord(String admin, Community community) throws Exception {
        Map<String, Object> body = newRecord();
        registered(admin, community.getId(), body);
        return body;
    }

    private Map<String, Object> registerAndAccept(User person, Community community, String admin) throws Exception {
        Map<String, Object> body = newRecord();
        body.put("personalId", person.getPersonalId());
        body.put("fullName", "Name in " + community.getId());
        UUID userId = registered(admin, community.getId(), body);
        assertThat(userId).isEqualTo(person.getId());
        invite(admin, community.getId(), userId).andExpect(status().isOk());
        String token = tokenFrom(awaitNextEmails(1)[0]);
        acceptAsUser(loginUser(person), token, CLIENT_IP).andExpect(status().isNoContent());
        return body;
    }

    private void promote(String admin, Community community, UUID userId) throws Exception {
        mockMvc.perform(patch("/api/v1/communities/{communityId}/memberships/{userId}", community.getId(), userId)
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + CommunityRole.COMMUNITY_ADMIN.name() + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID userIdOf(String personalId) {
        return (UUID) accountRow(personalId).get("id");
    }

    // --- Requests ---

    private ResultActions updateRecord(String bearer, UUID communityId, UUID userId, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(put("/api/v1/communities/{communityId}/memberships/{userId}/record", communityId,
                userId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode rosterEntry(String bearer, Community community, UUID userId) throws Exception {
        JsonNode roster = json(mockMvc.perform(get("/api/v1/communities/{communityId}/memberships",
                community.getId()).header(HttpHeaders.AUTHORIZATION, bearer)));
        for (JsonNode entry : roster) {
            if (entry.get("user").get("id").asText().equals(userId.toString())) {
                return entry;
            }
        }
        throw new AssertionError("No roster entry for user " + userId);
    }

    private JsonNode supply(String bearer, Supply supply) throws Exception {
        return json(mockMvc.perform(get("/api/v1/supplies/{supplyId}", supply.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer))).get("ownerRecord");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static List<String> recipientsOf(MimeMessage message) throws Exception {
        return java.util.Arrays.stream(message.getAllRecipients()).map(Object::toString).toList();
    }

    // --- Assertions ---

    private static void assertResponseShows(JsonNode response, Map<String, Object> record) {
        assertThat(List.of(text(response.get("fullName")), text(response.get("memberNumber")),
                text(response.get("contactEmail")), text(response.get("address")),
                text(response.get("phoneNumber")))).isEqualTo(expected(record));
    }

    /**
     * The member record of a roster entry, or of a supply's owner, under its own names (#448).
     */
    private static void assertShowsMemberData(JsonNode memberRecord, Map<String, Object> record) {
        assertResponseShows(memberRecord, record);
    }

    private static void assertRowShows(Map<String, Object> row, Map<String, Object> record) {
        assertThat(List.of(String.valueOf(row.get("full_name")), String.valueOf(row.get("member_number")),
                String.valueOf(row.get("contact_email")), String.valueOf(row.get("address")),
                String.valueOf(row.get("phone_number")))).isEqualTo(expected(record));
    }

    private static void assertRecordIsEmptyBut(String fullName, Map<String, Object> row) {
        assertThat(row.get("full_name")).isEqualTo(fullName);
        assertThat(row.get("member_number")).isNull();
        assertThat(row.get("contact_email")).isNull();
        assertThat(row.get("address")).isNull();
        assertThat(row.get("phone_number")).isNull();
    }

    private static List<String> expected(Map<String, Object> record) {
        return List.of(String.valueOf(record.get("fullName")), String.valueOf(record.get("memberNumber")),
                String.valueOf(record.get("contactEmail")), String.valueOf(record.get("address")),
                String.valueOf(record.get("phoneNumber")));
    }

    private static Map<String, Object> withoutRecord(Map<String, Object> row) {
        Map<String, Object> rest = new HashMap<>(row);
        RECORD_COLUMNS.forEach(rest::remove);
        return rest;
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? "null" : value.asText();
    }
}
