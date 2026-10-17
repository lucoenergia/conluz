package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipService;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.admin.user.token.CreateOneTimeTokenService;
import org.lucoenergia.conluz.domain.admin.user.token.OneTimeTokenPurpose;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SaveSharingAgreementFileRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.sharingagreementfile.SharingAgreementFile;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.update.UpdateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.lucoenergia.conluz.domain.admin.user.DefaultUserAdminMother.PERSONAL_ID;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DeleteUserControllerTest extends BaseControllerTest {

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipService createMembershipService;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private RegisterMemberService registerMemberService;
    @Autowired
    private CreateOneTimeTokenService createOneTimeTokenService;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private UpdateSharingAgreementRepository updateSharingAgreementRepository;
    @Autowired
    private SaveSharingAgreementFileRepository saveSharingAgreementFileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    void testDeleteUser() throws Exception {

        // Create a user
        User user = UserMother.randomUser();
        createUserRepository.create(user);
        Assertions.assertTrue(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));

        // Login as default admin
        String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(delete(String.format("/api/v1/users/%s", user.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        Assertions.assertFalse(getUserRepository.existsByPersonalId(UserPersonalId.of(user.getPersonalId())));
    }

    @Test
    void testWithUnknownUser() throws Exception {

        String authHeader = loginAsDefaultPlatformAdmin();

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void
    testWithoutIdInPath() throws Exception {
        final String authHeader = loginAsDefaultPlatformAdmin();

        mockMvc.perform(delete("/api/v1/users")
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

        mockMvc.perform(delete("/api/v1/users")
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

        final String userId = UUID.randomUUID().toString();

        mockMvc.perform(delete(String.format("/api/v1/users/%s", userId))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    @DisplayName("ACC-008 a community admin deleting a member of their community is refused, and the account is kept")
    void aCommunityAdminDeletingAMemberIsForbidden() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = UserMother.randomUser();
        member.setEnabled(true);
        createUserRepository.create(member);
        createMembershipService.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);

        mockMvc.perform(delete(String.format("/api/v1/users/%s", member.getId()))
                        .header(HttpHeaders.AUTHORIZATION, loginAsCommunityAdmin(community.getId()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        User stored = getUserRepository.findById(UserId.of(member.getId())).orElseThrow();
        Assertions.assertTrue(stored.isEnabled());
    }

    @Test
    @DisplayName("ACC-008 a community admin deleting a platform admin who is a member of their community is refused")
    void aCommunityAdminDeletingThePlatformAdminOfTheirCommunityIsForbidden() throws Exception {

        // Initialize the default platform admin.
        init();

        // Create a community and add the default platform admin as a member.
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User defaultAdmin = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();
        createMembershipService.create(community.getId(), defaultAdmin.getId(), CommunityRole.COMMUNITY_MEMBER);

        // Create a community admin of that community (not a platform admin) and log in.
        String authHeader = loginAsCommunityAdmin(community.getId());

        // Deleting the account would remove them from every community: not a community admin's to do.
        mockMvc.perform(delete(String.format("/api/v1/users/%s", defaultAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        // The default admin still exists, still enabled.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get().isEnabled());
    }

    /**
     * Only a platform admin may delete another user, and they count as an enabled platform admin themselves, so the
     * last one can only be "left" when the target is already disabled: that is the one path to the 409 left.
     */
    @Test
    void testDeleteLastPlatformAdminIsRejected() throws Exception {

        // The default platform admin is the only enabled one; the target is a disabled platform admin.
        String authHeader = loginAsDefaultPlatformAdmin();
        User disabledAdmin = UserMother.randomUser();
        disabledAdmin.setEnabled(false);
        createUserRepository.create(disabledAdmin);
        managePlatformAdminRepository.grant(UserId.of(disabledAdmin.getId()));

        mockMvc.perform(delete(String.format("/api/v1/users/%s", disabledAdmin.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_LAST_PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.errors[0].params").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        // The target still exists.
        Assertions.assertTrue(getUserRepository.findById(UserId.of(disabledAdmin.getId())).isPresent());
    }

    @Test
    void testCannotActOnOwnAccount() throws Exception {
        // Nobody may delete themselves, platform admin included: the guard settles visibility
        // first and then refuses because the target is the caller -- a 403, not a 404.
        String authHeader = loginAsDefaultPlatformAdmin();

        User self = getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).get();

        mockMvc.perform(delete(String.format("/api/v1/users/%s", self.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(HttpStatus.FORBIDDEN.value()));

        // The account is still there.
        Assertions.assertTrue(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).isPresent());
    }

    @Test
    @DisplayName("ACC-009 deleting a user deletes their memberships in every community and all their tokens, and no "
            + "other user's membership")
    void deletingAUserDeletesTheirMembershipsAndTokens() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        UserId admin = defaultAdminId();
        Community first = newCommunity();
        Community second = newCommunity();
        Community third = newCommunity();
        User member = UserMother.randomUser();
        member.setEnabled(true);
        createUserRepository.create(member);
        UserId memberId = UserId.of(member.getId());
        // Pending memberships, through the registration flow, each with an invitation token
        register(first, admin, member);
        register(second, admin, member);
        createOneTimeTokenService.issueForMembership(memberId, first.getId(), OneTimeTokenPurpose.INVITATION);
        createOneTimeTokenService.issueForMembership(memberId, second.getId(), OneTimeTokenPurpose.INVITATION);
        // An accepted membership, and a token bound only to the user
        createMembershipService.create(third.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        createOneTimeTokenService.issue(memberId, OneTimeTokenPurpose.PASSWORD_RESET);
        // Another user in the same communities, with their own token
        User other = UserMother.randomUser();
        createUserRepository.create(other);
        register(first, admin, other);
        createMembershipService.create(third.getId(), other.getId(), CommunityRole.COMMUNITY_MEMBER);
        createOneTimeTokenService.issueForMembership(UserId.of(other.getId()), first.getId(),
                OneTimeTokenPurpose.INVITATION);
        Assertions.assertEquals(3, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", member.getId()));
        Assertions.assertEquals(3, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));

        mockMvc.perform(delete(String.format("/api/v1/users/%s", member.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Assertions.assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", member.getId()));
        Assertions.assertEquals(0, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", member.getId()));
        Assertions.assertEquals(0, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));
        Assertions.assertEquals(2, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", other.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", other.getId()));
    }

    @Test
    @DisplayName("ACC-009 deleting a user who owns a supply is refused with 409 USER_OWNS_SUPPLIES, and nothing is "
            + "deleted")
    void deletingASupplyOwnerIsRefused() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        UserId admin = defaultAdminId();
        Community community = newCommunity();
        Community other = newCommunity();
        User owner = UserMother.randomUser();
        owner.setEnabled(true);
        createUserRepository.create(owner);
        createMembershipService.create(community.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        register(other, admin, owner);
        createOneTimeTokenService.issueForMembership(UserId.of(owner.getId()), other.getId(),
                OneTimeTokenPurpose.INVITATION);
        Supply supply = createSupplyService.create(SupplyMother.random(owner).build(),
                UserPersonalId.of(owner.getPersonalId()), community.getId());

        mockMvc.perform(delete(String.format("/api/v1/users/%s", owner.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors[0].code").value("USER_OWNS_SUPPLIES"))
                .andExpect(jsonPath("$.errors[0].params.userId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        Assertions.assertEquals(1, count("SELECT count(*) FROM users WHERE id = ?", owner.getId()));
        Assertions.assertEquals(2, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", owner.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", owner.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ? AND user_id = ?", supply.getId(),
                owner.getId()));
    }

    @Test
    @DisplayName("MEM-003 deleting the user recorded as the inviter of other memberships keeps those memberships, "
            + "with no inviter and their invitation instant")
    void deletingTheInviterKeepsTheMembershipsTheyInvited() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        Community community = newCommunity();
        User inviter = UserMother.randomUser();
        createUserRepository.create(inviter);
        User invited = UserMother.randomUser();
        createUserRepository.create(invited);
        register(community, UserId.of(inviter.getId()), invited);
        createOneTimeTokenService.issueForMembership(UserId.of(invited.getId()), community.getId(),
                OneTimeTokenPurpose.INVITATION);
        Assertions.assertEquals(inviter.getId(), invitedBy(invited, community));
        Instant invitedAt = invitedAt(invited, community);

        mockMvc.perform(delete(String.format("/api/v1/users/%s", inviter.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Assertions.assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", inviter.getId()));
        Assertions.assertNull(invitedBy(invited, community));
        Assertions.assertEquals(invitedAt, invitedAt(invited, community));
        Assertions.assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", invited.getId()));
    }

    @Test
    @DisplayName("ACC-009 deleting a user referenced by sharing agreements and their files keeps them, with no "
            + "creator, editor or uploader, and is never answered 500")
    void deletingAUserReferencedBySharingAgreementsKeepsThem() throws Exception {
        String authHeader = loginAsDefaultPlatformAdmin();
        UserId admin = defaultAdminId();
        Community community = newCommunity();
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        createMembershipService.create(community.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        Supply supply = createSupplyService.create(SupplyMother.random(owner).build(),
                UserPersonalId.of(owner.getPersonalId()), community.getId());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        // The user to delete: a member who created, edited and uploaded the evidence of an agreement, invited
        // someone, and has tokens
        User editor = UserMother.randomUser();
        createUserRepository.create(editor);
        register(community, admin, editor);
        createOneTimeTokenService.issueForMembership(UserId.of(editor.getId()), community.getId(),
                OneTimeTokenPurpose.INVITATION);
        User invited = UserMother.randomUser();
        createUserRepository.create(invited);
        register(community, UserId.of(editor.getId()), invited);
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .withCreatedBy(editor.getId())
                .build());
        updateSharingAgreementRepository.update(plant.getId(), agreement.getId(), new UpdateSharingAgreement.Builder()
                .withName(agreement.getName())
                .withInstalledPowerKw(BigDecimal.ONE)
                .withUpdatedBy(editor.getId())
                .build());
        SharingAgreementFile file = saveSharingAgreementFileRepository.save(new SharingAgreementFile.Builder()
                .withId(UUID.randomUUID())
                .withSharingAgreementId(agreement.getId())
                .withFilename("distributor.txt")
                .withContent(new byte[]{1})
                .withContentHash("hash")
                .withUploadedAt(Instant.now())
                .withUploadedBy(editor.getId())
                .build(), plant.getId());
        Assertions.assertEquals(1, count("SELECT count(*) FROM sharing_agreement WHERE id = ? AND created_by = ? "
                + "AND updated_by = ?", agreement.getId(), editor.getId(), editor.getId()));

        mockMvc.perform(delete(String.format("/api/v1/users/%s", editor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Assertions.assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", editor.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM sharing_agreement WHERE id = ? AND created_by IS NULL "
                + "AND updated_by IS NULL", agreement.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM sharing_agreement_file WHERE id = ? "
                + "AND uploaded_by IS NULL", file.getId()));
        Assertions.assertNull(invitedBy(invited, community));
        Assertions.assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
    }

    @Test
    void theApiDocsDeclareTheConflictsOfDeletingAUser() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users/{userId}'].delete.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{userId}'].delete.responses['409'].content"
                        + "['application/json'].examples['Owns supplies'].value.errors[0].code")
                        .value("USER_OWNS_SUPPLIES"))
                .andExpect(jsonPath("$.paths['/api/v1/users/{userId}'].delete.responses['409'].content"
                        + "['application/json'].examples['Last platform admin'].value.errors[0].code")
                        .value("USER_LAST_PLATFORM_ADMIN"));
    }

    private Community newCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    private UserId defaultAdminId() {
        return UserId.of(getUserRepository.findByPersonalId(UserPersonalId.of(PERSONAL_ID)).orElseThrow().getId());
    }

    private void register(Community community, UserId registeredBy, User user) {
        registerMemberService.register(community.getId(), registeredBy, UserPersonalId.of(user.getPersonalId()),
                new MemberRecord(user.getFullName(), null, "member-" + UUID.randomUUID() + "@example.org", null, null));
    }

    /**
     * Counts with plain SQL, after flushing what the request left in the persistence context: the test's transaction
     * is still open, and what is asserted is what the database holds.
     */
    private int count(String sql, Object... parameters) {
        entityManager.flush();
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
    }

    private UUID invitedBy(User user, Community community) {
        entityManager.flush();
        List<UUID> rows = jdbcTemplate.queryForList("SELECT invited_by FROM community_memberships WHERE user_id = ? "
                + "AND community_id = ?", UUID.class, user.getId(), community.getId());
        Assertions.assertEquals(1, rows.size(), "The membership should exist");
        return rows.get(0);
    }

    private Instant invitedAt(User user, Community community) {
        entityManager.flush();
        List<java.sql.Timestamp> rows = jdbcTemplate.queryForList("SELECT invited_at FROM community_memberships "
                + "WHERE user_id = ? AND community_id = ?", java.sql.Timestamp.class, user.getId(), community.getId());
        Assertions.assertEquals(1, rows.size(), "The membership should exist");
        return rows.get(0) == null ? null : rows.get(0).toInstant();
    }
}
