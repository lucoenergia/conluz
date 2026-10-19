package org.lucoenergia.conluz.infrastructure.admin.community.membership.register;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.access.CommunityAccessGuard;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The server can send invitations, so that nothing being sent is down to registration alone.
 */
class RegisterMemberControllerTest extends BaseSendingMemberTest {

    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private CommunityAccessGuard communityAccessGuard;

    // --- AC1: a new DNI ---

    @Test
    @DisplayName("ACC-006 a new DNI gets an account without a password, a number or a password to change, "
            + "with the contact email as its email")
    void aNewDni_getsAnAccountWithoutAPassword() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        String dni = UserMother.randomPersonalId();
        String digits = dni.substring(0, 8);
        // Typed with separators and a lower-case letter: the account is stored under the normalised form
        body.put("personalId", digits.substring(0, 2) + "." + digits.substring(2, 5) + "." + digits.substring(5)
                + "-" + dni.substring(8).toLowerCase(Locale.ROOT));

        register(loginAsAdminOf(community), community.getId(), body).andExpect(status().isOk());

        Map<String, Object> account = accountRow(dni);
        assertThat(account.get("password")).isNull();
        assertThat(account.get("number")).isNull();
        assertThat(account.get("must_change_password")).isEqualTo(false);
        assertThat(account.get("email")).isEqualTo(body.get("contactEmail"));
        assertThat(account.get("full_name")).isEqualTo(body.get("fullName"));
        assertThat(account.get("is_platform_admin")).isEqualTo(false);
        assertThat(account.get("enabled")).isEqualTo(true);
    }

    @Test
    @DisplayName("MEM-007 a new DNI gets a pending membership holding the record exactly as it was entered")
    void aNewDni_getsAPendingMembershipWithTheEnteredRecord() throws Exception {
        Community community = newCommunity();
        User admin = memberOf(community, CommunityRole.COMMUNITY_ADMIN);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");

        register(loginUser(admin), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.community.id").value(community.getId().toString()))
                .andExpect(jsonPath("$.role").value("COMMUNITY_MEMBER"))
                .andExpect(jsonPath("$.personalId").value(personalId))
                .andExpect(jsonPath("$.fullName").value(body.get("fullName")))
                .andExpect(jsonPath("$.memberNumber").value(body.get("memberNumber")))
                .andExpect(jsonPath("$.contactEmail").value(body.get("contactEmail")))
                .andExpect(jsonPath("$.address").value(body.get("address")))
                .andExpect(jsonPath("$.phoneNumber").value(body.get("phoneNumber")))
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.invitedAt").isEmpty());

        Map<String, Object> membership = membershipRow(personalId, community.getId());
        assertThat(membership.get("role")).isEqualTo("COMMUNITY_MEMBER");
        assertThat(membership.get("accepted_at")).isNull();
        assertThat(membership.get("enabled")).isEqualTo(true);
        assertThat(membership.get("invited_by")).isEqualTo(admin.getId());
        assertThat(membership.get("invited_at")).isNull();
        assertThat(membership.get("full_name")).isEqualTo(body.get("fullName"));
        assertThat(membership.get("member_number")).isEqualTo(body.get("memberNumber"));
        assertThat(membership.get("contact_email")).isEqualTo(body.get("contactEmail"));
        assertThat(membership.get("address")).isEqualTo(body.get("address"));
        assertThat(membership.get("phone_number")).isEqualTo(body.get("phoneNumber"));
    }

    @Test
    @DisplayName("MEM-007 a blank contact email is recorded as none, not refused")
    void aBlankContactEmail_isRecordedAsNone() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        body.put("contactEmail", "  ");

        register(loginAsAdminOf(community), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").isEmpty());

        assertThat(membershipRow((String) body.get("personalId"), community.getId()).get("contact_email")).isNull();
        assertThat(accountRow((String) body.get("personalId")).get("email")).isNull();
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-007 empty or blank optional values of the record are recorded as none, and nothing is trimmed")
    void emptyOrBlankOptionalValues_areRecordedAsNone() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        body.put("fullName", "  Lucía  ");
        body.remove("memberNumber");
        body.put("contactEmail", "");
        body.put("address", "");
        body.put("phoneNumber", "   ");

        register(loginAsAdminOf(community), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("  Lucía  "))
                .andExpect(jsonPath("$.memberNumber").isEmpty())
                .andExpect(jsonPath("$.contactEmail").isEmpty())
                .andExpect(jsonPath("$.address").isEmpty())
                .andExpect(jsonPath("$.phoneNumber").isEmpty());

        Map<String, Object> membership = membershipRow((String) body.get("personalId"), community.getId());
        assertThat(membership.get("member_number")).isNull();
        assertThat(membership.get("contact_email")).isNull();
        assertThat(membership.get("address")).isNull();
        assertThat(membership.get("phone_number")).isNull();
        assertThat(accountRow((String) body.get("personalId")).get("email")).isNull();
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-007 registration issues no token and sends nothing, even when the record has a contact email")
    void registration_sendsNothing() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");

        String response = register(loginAsAdminOf(community), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value(body.get("contactEmail")))
                .andExpect(jsonPath("$.invitedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(tokenCount(personalId)).isZero();
        assertThat(membershipRow(personalId, community.getId()).get("invited_at")).isNull();
        verify(emailSender, never()).send(any());
        assertNoSecretIn(response.replace((String) body.get("contactEmail"), ""));
    }

    // --- AC2: a DNI that exists elsewhere ---

    @Test
    @DisplayName("MEM-008 a DNI with an account elsewhere is answered exactly like a new one, and its account is "
            + "left unchanged")
    void aDniWithAnAccountElsewhere_isAnsweredLikeANewOne() throws Exception {
        Community community = newCommunity();
        Community elsewhere = newCommunity();
        User existing = memberOf(elsewhere, CommunityRole.COMMUNITY_MEMBER);
        Map<String, Object> accountBefore = accountRow(existing.getPersonalId());
        String bearer = loginAsAdminOf(community);

        Map<String, Object> newBody = newRecord();
        Map<String, Object> existingBody = new java.util.LinkedHashMap<>(newBody);
        existingBody.put("personalId", existing.getPersonalId());
        existingBody.put("contactEmail", newEmail());
        newBody.put("contactEmail", newEmail());

        String forNew = register(bearer, community.getId(), newBody)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String forExisting = register(bearer, community.getId(), existingBody)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // Apart from the ids, which are random either way, and what was typed differently, the two responses are
        // the same bytes
        String comparableNew = forNew
                .replace(json(forNew).get("id").asText(), "<id>")
                .replace(json(forNew).get("user").get("id").asText(), "<userId>")
                .replace((String) newBody.get("personalId"), "<personalId>")
                .replace((String) newBody.get("contactEmail"), "<contactEmail>");
        String comparableExisting = forExisting
                .replace(json(forExisting).get("id").asText(), "<id>")
                .replace(json(forExisting).get("user").get("id").asText(), "<userId>")
                .replace(existing.getPersonalId(), "<personalId>")
                .replace((String) existingBody.get("contactEmail"), "<contactEmail>");
        assertThat(comparableExisting).isEqualTo(comparableNew);

        // The record is the one entered, not the account's data
        JsonNode response = json(forExisting);
        assertThat(response.get("fullName").asText()).isEqualTo(existingBody.get("fullName"))
                .isNotEqualTo(existing.getFullName());
        assertThat(response.get("contactEmail").asText()).isEqualTo(existingBody.get("contactEmail"));
        assertThat(forExisting).doesNotContain(existing.getEmail());

        // The account is untouched
        assertThat(accountRow(existing.getPersonalId())).isEqualTo(accountBefore);
        assertThat(accountCount(existing.getPersonalId())).isEqualTo(1);

        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-008 two concurrent registrations of the same new DNI in two communities both succeed, with "
            + "one account")
    void twoConcurrentRegistrationsOfTheSameNewDni_bothSucceed_withOneAccount() throws Exception {
        Community first = newCommunity();
        Community second = newCommunity();
        String firstAdmin = loginAsAdminOf(first);
        String secondAdmin = loginAsAdminOf(second);
        Map<String, Object> firstBody = newRecord();
        Map<String, Object> secondBody = newRecord();
        String personalId = (String) firstBody.get("personalId");
        secondBody.put("personalId", personalId);

        // Both lookups find no account before either registration creates one, so both try to create it
        CountDownLatch bothLookedUp = new CountDownLatch(2);
        doAnswer(invocation -> {
            Object found = invocation.callRealMethod();
            bothLookedUp.countDown();
            assertThat(bothLookedUp.await(10, TimeUnit.SECONDS)).isTrue();
            return found;
        }).when(getUserRepository).findByPersonalId(argThat((UserPersonalId id) ->
                id != null && personalId.equals(UserPersonalId.normalize(id.getPersonalId()))));

        List<Object> outcomes = race(
                () -> register(firstAdmin, first.getId(), firstBody).andReturn().getResponse(),
                () -> register(secondAdmin, second.getId(), secondBody).andReturn().getResponse());

        for (Object outcome : outcomes) {
            assertThat(outcome).isInstanceOf(org.springframework.mock.web.MockHttpServletResponse.class);
            org.springframework.mock.web.MockHttpServletResponse response =
                    (org.springframework.mock.web.MockHttpServletResponse) outcome;
            assertThat(response.getContentAsString()).doesNotContain("USER_ALREADY_EXISTS");
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(bothLookedUp.getCount()).isZero();
        assertThat(accountCount(personalId)).isEqualTo(1);
        assertThat(membershipCount(personalId)).isEqualTo(2);
    }

    // --- AC3: a DNI already in this community ---

    @Test
    @DisplayName("MEM-007 a DNI with a pending membership in this community is answered 409, and nothing changes")
    void aDniWithAPendingMembershipHere_isAnswered409() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        register(bearer, community.getId(), body).andExpect(status().isOk());
        Map<String, Object> accountBefore = accountRow(personalId);
        Map<String, Object> membershipBefore = membershipRow(personalId, community.getId());
        org.mockito.Mockito.clearInvocations(emailSender);

        Map<String, Object> again = newRecord();
        again.put("personalId", personalId);
        String response = register(bearer, community.getId(), again)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(accountRow(personalId)).isEqualTo(accountBefore);
        assertThat(membershipRow(personalId, community.getId())).isEqualTo(membershipBefore);
        assertThat(membershipCount(personalId)).isEqualTo(1);
        assertThat(tokenCount(personalId)).isZero();
        verify(emailSender, never()).send(any());
        assertNoSecretIn(response);
    }

    @Test
    @DisplayName("MEM-007 a DNI with an active membership in this community is answered 409, and nothing changes")
    void aDniWithAnActiveMembershipHere_isAnswered409() throws Exception {
        Community community = newCommunity();
        User member = memberOf(community, CommunityRole.COMMUNITY_MEMBER);
        Map<String, Object> accountBefore = accountRow(member.getPersonalId());
        Map<String, Object> membershipBefore = membershipRow(member.getPersonalId(), community.getId());
        Map<String, Object> body = newRecord();
        body.put("personalId", member.getPersonalId());

        String response = register(loginAsAdminOf(community), community.getId(), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(accountRow(member.getPersonalId())).isEqualTo(accountBefore);
        assertThat(membershipRow(member.getPersonalId(), community.getId())).isEqualTo(membershipBefore);
        assertThat(tokenCount(member.getPersonalId())).isZero();
        verify(emailSender, never()).send(any());
        assertNoSecretIn(response);
    }

    // --- AC4: no contact email ---

    @Test
    @DisplayName("MEM-007 without a contact email the record and the account have no email, and nothing is sent")
    void withoutAContactEmail_nothingIsSent() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();
        body.remove("contactEmail");
        String personalId = (String) body.get("personalId");

        register(loginAsAdminOf(community), community.getId(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").isEmpty())
                .andExpect(jsonPath("$.invitedAt").isEmpty());

        assertThat(tokenCount(personalId)).isZero();
        assertThat(membershipRow(personalId, community.getId()).get("invited_at")).isNull();
        assertThat(accountRow(personalId).get("email")).isNull();
        verify(emailSender, never()).send(any());
        assertThat(logs.allOfThisThread()).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).isEqualTo("Member registered: personalId="
                        + UserPersonalId.mask(personalId) + ", communityId=" + community.getId()));
    }

    // --- AC5: a pending member ---

    @Test
    @DisplayName("MEM-004 a registered member has no access to the community, and can own a supply in it")
    void aRegisteredMember_hasNoAccess_andCanOwnASupply() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        Map<String, Object> body = newRecord();
        String personalId = (String) body.get("personalId");
        register(bearer, community.getId(), body).andExpect(status().isOk());

        User registered = (User) userDetailsService.loadUserByUsername(personalId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(registered, null, registered.getAuthorities()));
        try {
            assertThatThrownBy(() -> communityAccessGuard.canReadCommunity(community.getId()))
                    .isInstanceOf(CommunityNotFoundException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }

        Map<String, Object> supply = Map.of(
                "code", SupplyMother.random().build().getCode(),
                "personalId", personalId,
                "address", "Calle Mayor 1, Luco",
                "communityId", community.getId().toString());
        mockMvc.perform(post("/api/v1/supplies")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(supply)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM supplies s JOIN users u ON u.id = s.user_id "
                + "WHERE u.personal_id = ? AND s.community_id = ?", Integer.class, personalId, community.getId()))
                .isEqualTo(1);
    }

    // --- AC6: who may register ---

    @Test
    @DisplayName("MEM-007 a platform admin who is not a community admin here is forbidden")
    void aPlatformAdmin_isForbidden() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();

        register(loginAsPlatformAdmin(), community.getId(), body).andExpect(status().isForbidden());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-007 a plain member of the community is forbidden")
    void aMemberOfTheCommunity_isForbidden() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();

        register(loginAsMemberOf(community), community.getId(), body).andExpect(status().isForbidden());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-007 a pending community admin is answered as a non-member")
    void aPendingAdmin_isAnsweredAsANonMember() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();

        register(loginAsPendingAdminOf(community), community.getId(), body).andExpect(status().isNotFound());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-007 a disabled community admin is answered as a non-member")
    void aDisabledAdmin_isAnsweredAsANonMember() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();

        register(loginAsDisabledAdminOf(community), community.getId(), body).andExpect(status().isNotFound());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    @DisplayName("MEM-007 an active admin of another community is answered as a non-member")
    void anAdminOfAnotherCommunity_isAnsweredAsANonMember() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();

        register(loginAsAdminOf(newCommunity()), community.getId(), body).andExpect(status().isNotFound());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    void anAnonymousCaller_isUnauthorized() throws Exception {
        Community community = newCommunity();
        Map<String, Object> body = newRecord();

        mockMvc.perform(post("/api/v1/communities/{communityId}/memberships/register", community.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());

        assertThat(accountCount((String) body.get("personalId"))).isZero();
    }

    @Test
    void anUnknownCommunity_isNotFound() throws Exception {
        register(loginAsAdminOf(newCommunity()), UUID.randomUUID(), newRecord()).andExpect(status().isNotFound());
    }

    @Test
    void anInvalidBody_isABadRequest_andCreatesNothing() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);

        Map<String, Object> withoutPersonalId = newRecord();
        withoutPersonalId.remove("personalId");
        Map<String, Object> blankFullName = newRecord();
        blankFullName.put("fullName", " ");
        Map<String, Object> invalidEmail = newRecord();
        invalidEmail.put("contactEmail", "not an email");
        Map<String, Object> negativeNumber = newRecord();
        negativeNumber.put("memberNumber", -1);

        for (Map<String, Object> body : List.of(withoutPersonalId, blankFullName, invalidEmail, negativeNumber)) {
            String response = register(bearer, community.getId(), body)
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();
            assertNoSecretIn(response);
        }
        for (Map<String, Object> body : List.of(blankFullName, invalidEmail, negativeNumber)) {
            assertThat(accountCount((String) body.get("personalId"))).isZero();
        }
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("MEM-007 a personal ID made only of characters normalisation removes is refused, and nothing is "
            + "created")
    void aPersonalIdThatNormalisesToNothing_isABadRequest_andCreatesNothing() throws Exception {
        Community community = newCommunity();
        String bearer = loginAsAdminOf(community);
        int membershipsBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_memberships WHERE community_id = ?", Integer.class, community.getId());

        for (String personalId : List.of("..-", " . ")) {
            Map<String, Object> body = newRecord();
            body.put("personalId", personalId);

            register(bearer, community.getId(), body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        assertThat(accountCount("")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships WHERE community_id = ?",
                Integer.class, community.getId())).isEqualTo(membershipsBefore);
        assertThat(tokenCount("")).isZero();
        verify(emailSender, never()).send(any());
        // Refused before any lookup: the only lookups are those authenticating the admin
        verify(getUserRepository, never()).findByPersonalId(argThat((UserPersonalId id) ->
                id != null && UserPersonalId.normalize(id.getPersonalId()).isEmpty()));
    }

    // --- AC8: the response ---

    @Test
    @DisplayName("MEM-008 the response carries exactly the membership, its state and the record, and no account field")
    void theResponse_hasExactlyTheMembershipFields() throws Exception {
        Community community = newCommunity();

        String response = register(loginAsAdminOf(community), community.getId(), newRecord())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = json(response);
        assertThat(fieldNames(json)).isEqualTo(new TreeSet<>(Set.of("id", "user", "community", "role", "personalId",
                "fullName", "memberNumber", "contactEmail", "address", "phoneNumber", "acceptedAt", "enabled",
                "invitedAt", "invitationExpiresAt", "capabilities")));
        assertThat(fieldNames(json.get("user"))).isEqualTo(new TreeSet<>(Set.of("id")));
        assertThat(json.get("user").get("id").asText())
                .isEqualTo(accountRow(json.get("personalId").asText()).get("id").toString());
        assertThat(fieldNames(json.get("community"))).isEqualTo(new TreeSet<>(Set.of("id", "name")));
        assertThat(fieldNames(json.get("capabilities"))).isEqualTo(new TreeSet<>(Set.of("canUpdateRole", "canDelete",
                "canManageInvestment", "canReadPayback", "canInvite", "canEnable", "canDisable",
                "canEditRecord")));
    }

    // --- Logging ---

    @Test
    @DisplayName("MEM-008 one line is logged per registration, with the masked DNI and the community, and nothing "
            + "about the account")
    void oneLineIsLogged_withTheMaskedDni() throws Exception {
        Community community = newCommunity();
        User existing = newUser();
        Map<String, Object> body = newRecord();
        body.put("personalId", existing.getPersonalId());
        logs.clear();

        register(loginAsAdminOf(community), community.getId(), body).andExpect(status().isOk());

        assertThat(logs.allOfThisThread())
                .filteredOn(event -> event.getFormattedMessage().startsWith("Member registered"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).isEqualTo("Member registered: personalId="
                            + UserPersonalId.mask(existing.getPersonalId()) + ", communityId=" + community.getId());
                });
        assertThat(logs.allOfThisThread()).noneSatisfy(event ->
                assertThat(event.getFormattedMessage()).contains(existing.getPersonalId()));
    }

    // --- Helpers ---

    private JsonNode json(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }

    /**
     * Runs both tasks on their own threads, released together once both are ready, and returns what each returned
     * or threw, in order. Fails if either has not finished within 30 seconds.
     */
    private static List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
