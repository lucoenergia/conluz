package org.lucoenergia.conluz.infrastructure.admin.user.profile;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lucoenergia.conluz.infrastructure.admin.supply.create.CreateSupplyRepositoryDatabase.DEFAULT_COMMUNITY_ID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /api/v1/users/profile} is the one write an ordinary member may perform on their own
 * record. It exists because the administrative endpoint, {@code PUT /users/{userId}}, replaces the
 * identifying fields as well — so the interesting assertions here are as much about what does
 * <em>not</em> change as about what does.
 */
@Transactional
class UpdateProfileControllerTest extends BaseControllerTest {

    private static final String URL = "/api/v1/users/profile";

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateMembershipService createMembershipService;

    @Test
    void aPlainMemberUpdatesTheirOwnContactDetails() throws Exception {
        User member = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(member);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nuevo@email.com", "Calle Nueva 1", "+34600111222")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo@email.com"))
                .andExpect(jsonPath("$.address").value("Calle Nueva 1"))
                .andExpect(jsonPath("$.phoneNumber").value("+34600111222"));

        User stored = reload(member);
        assertEquals("nuevo@email.com", stored.getEmail());
        assertEquals("Calle Nueva 1", stored.getAddress());
        assertEquals("+34600111222", stored.getPhoneNumber());
    }

    @Test
    void theIdentifyingFieldsAreUntouched() throws Exception {
        // The whole reason this endpoint exists rather than a self branch on canEditUser.
        User member = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(member);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("otro@email.com", "Otra calle", "+34600333444")))
                .andExpect(status().isOk());

        User stored = reload(member);
        assertEquals(member.getPersonalId(), stored.getPersonalId());
        assertEquals(member.getFullName(), stored.getFullName());
        assertEquals(member.getNumber(), stored.getNumber());
    }

    @Test
    void theBodyCannotCarryAnIdentifyingField() throws Exception {
        // Unknown properties are rejected, so a client cannot smuggle personalId past the narrow body.
        User member = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(member);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "x@email.com", "personalId": "99999999Z"}"""))
                .andDo(print())
                .andExpect(status().isBadRequest());

        assertEquals(member.getPersonalId(), reload(member).getPersonalId());
    }

    @Test
    void aPlatformAdminMayAlsoUseIt() throws Exception {
        // It is not a role-scoped endpoint: it acts on whoever is calling.
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin@email.com", "Sede", "+34600555666")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@email.com"));
    }

    @Test
    void addressAndPhoneAreOptionalAndClearedWhenOmitted() throws Exception {
        User member = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(member);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "solo@email.com"}"""))
                .andExpect(status().isOk());

        User stored = reload(member);
        assertEquals("solo@email.com", stored.getEmail());
        assertEquals(null, stored.getAddress());
        assertEquals(null, stored.getPhoneNumber());
    }

    @Test
    void emailIsMandatory() throws Exception {
        User member = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(member);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address": "Calle Nueva 1"}"""))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()));
    }

    @Test
    void aMalformedEmailIsRejected() throws Exception {
        User member = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(member);

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-an-email", null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutATokenItIsUnauthorized() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nuevo@email.com", null, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void itAlwaysActsOnTheCallerAndCannotBeAimedAtAnotherUser() throws Exception {
        // There is no guard method behind this endpoint because there is nothing to scope: the
        // target is the authenticated principal, never a caller-supplied id. This test is what keeps
        // that true -- adding a {userId} path variable or a userId body field to "make it flexible"
        // has to fail here.
        User caller = enabledMemberOfDefaultCommunity();
        User victim = enabledMemberOfDefaultCommunity();
        String victimEmailBefore = reload(victim).getEmail();
        String authHeader = loginUser(caller);

        // A userId in the body is an unknown property -> 400, nothing written at all.
        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"email\": \"tomado@email.com\", \"userId\": \"%s\"}", victim.getId())))
                .andDo(print())
                .andExpect(status().isBadRequest());

        // And the legitimate call touches the caller only.
        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("solo-el-llamante@email.com", null, null)))
                .andExpect(status().isOk());

        assertEquals("solo-el-llamante@email.com", reload(caller).getEmail());
        assertEquals(victimEmailBefore, reload(victim).getEmail());
    }

    @Test
    void thereIsNoPerUserVariantOfThisEndpoint() throws Exception {
        // PUT /users/{userId} exists and is administrative; PUT /users/profile/{userId} must not.
        User caller = enabledMemberOfDefaultCommunity();
        User victim = enabledMemberOfDefaultCommunity();
        String authHeader = loginUser(caller);

        mockMvc.perform(put(URL + "/" + victim.getId())
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nope@email.com", null, null)))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private User enabledMemberOfDefaultCommunity() throws Exception {
        User member = UserMother.randomUser();
        member.enable();
        createUserRepository.create(member);
        createMembershipService.create(DEFAULT_COMMUNITY_ID, member.getId(), CommunityRole.COMMUNITY_MEMBER);
        return member;
    }

    private User reload(User user) {
        return getUserRepository.findByPersonalId(UserPersonalId.of(user.getPersonalId())).orElseThrow();
    }

    private static String body(String email, String address, String phoneNumber) {
        StringBuilder json = new StringBuilder("{\"email\": \"").append(email).append("\"");
        if (address != null) {
            json.append(", \"address\": \"").append(address).append("\"");
        }
        if (phoneNumber != null) {
            json.append(", \"phoneNumber\": \"").append(phoneNumber).append("\"");
        }
        return json.append("}").toString();
    }
}
