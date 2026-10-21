package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.register.RegisterMemberService;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserService;
import org.lucoenergia.conluz.domain.admin.user.delete.UserOwnsSuppliesException;
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
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What deleting a user does (ACC-009, MEM-003), exercised on the service. No caller may delete another user's account
 * over HTTP (ACC-008, ACC-020), so the rules of the deletion itself are pinned here, where they still run.
 */
@Transactional
class DeleteUserServiceRulesTest extends BaseIntegrationTest {

    @Autowired
    private DeleteUserService deleteUserService;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
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
    void deletingAUserRemovesTheAccount() {
        User user = UserMother.randomUser();
        createUserRepository.create(user);

        deleteUserService.delete(UserId.of(user.getId()));

        Assertions.assertTrue(getUserRepository.findById(UserId.of(user.getId())).isEmpty());
    }

    /**
     * A disabled platform admin does not count towards the last-platform-admin rule, so deleting one is allowed while
     * only one platform admin is enabled. The refusal itself is covered by DeleteUserServiceTest.
     */
    @Test
    @DisplayName("ACC-009 deleting a disabled platform admin while only one platform admin is enabled is allowed")
    void deletingADisabledPlatformAdminWithOneEnabledAdminIsAllowed() {
        User enabledAdmin = newPlatformAdmin(true);
        User disabledAdmin = newPlatformAdmin(false);

        deleteUserService.delete(UserId.of(disabledAdmin.getId()));

        // The target is gone, and the one enabled platform admin is still there.
        Assertions.assertTrue(getUserRepository.findById(UserId.of(disabledAdmin.getId())).isEmpty());
        Assertions.assertTrue(getUserRepository.findById(UserId.of(enabledAdmin.getId())).orElseThrow().isEnabled());
    }

    @Test
    @DisplayName("ACC-009 deleting a user deletes their memberships in every community and all their tokens, and no "
            + "other user's membership")
    void deletingAUserDeletesTheirMembershipsAndTokens() {
        UserId admin = newAdmin();
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
        createMembershipRepository.create(third.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        createOneTimeTokenService.issue(memberId, OneTimeTokenPurpose.PASSWORD_RESET);
        // Another user in the same communities, with their own token
        User other = UserMother.randomUser();
        createUserRepository.create(other);
        register(first, admin, other);
        createMembershipRepository.create(third.getId(), other.getId(), CommunityRole.COMMUNITY_MEMBER);
        createOneTimeTokenService.issueForMembership(UserId.of(other.getId()), first.getId(),
                OneTimeTokenPurpose.INVITATION);
        Assertions.assertEquals(3, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", member.getId()));
        Assertions.assertEquals(3, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));

        deleteUserService.delete(memberId);

        Assertions.assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", member.getId()));
        Assertions.assertEquals(0, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", member.getId()));
        Assertions.assertEquals(0, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", member.getId()));
        Assertions.assertEquals(2, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", other.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", other.getId()));
    }

    @Test
    @DisplayName("ACC-009 deleting a user who owns a supply is refused with USER_OWNS_SUPPLIES, and nothing is "
            + "deleted")
    void deletingASupplyOwnerIsRefused() {
        UserId admin = newAdmin();
        Community community = newCommunity();
        Community other = newCommunity();
        User owner = UserMother.randomUser();
        owner.setEnabled(true);
        createUserRepository.create(owner);
        createMembershipRepository.create(community.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
        register(other, admin, owner);
        createOneTimeTokenService.issueForMembership(UserId.of(owner.getId()), other.getId(),
                OneTimeTokenPurpose.INVITATION);
        Supply supply = createSupplyService.create(SupplyMother.random(owner).build(),
                UserPersonalId.of(owner.getPersonalId()), community.getId());

        UserOwnsSuppliesException refusal = Assertions.assertThrows(UserOwnsSuppliesException.class,
                () -> deleteUserService.delete(UserId.of(owner.getId())));

        Assertions.assertEquals(UserId.of(owner.getId()), refusal.getUserId());
        Assertions.assertEquals(1, count("SELECT count(*) FROM users WHERE id = ?", owner.getId()));
        Assertions.assertEquals(2, count("SELECT count(*) FROM community_memberships WHERE user_id = ?", owner.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", owner.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ? AND user_id = ?", supply.getId(),
                owner.getId()));
    }

    @Test
    @DisplayName("MEM-003 deleting the user recorded as the inviter of other memberships keeps those memberships, "
            + "with no inviter and their invitation instant")
    void deletingTheInviterKeepsTheMembershipsTheyInvited() {
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

        deleteUserService.delete(UserId.of(inviter.getId()));

        Assertions.assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", inviter.getId()));
        Assertions.assertNull(invitedBy(invited, community));
        Assertions.assertEquals(invitedAt, invitedAt(invited, community));
        Assertions.assertEquals(1, count("SELECT count(*) FROM one_time_token WHERE user_id = ?", invited.getId()));
    }

    @Test
    @DisplayName("ACC-009 deleting a user referenced by sharing agreements and their files keeps them, with no "
            + "creator, editor or uploader, and never fails with a database error")
    void deletingAUserReferencedBySharingAgreementsKeepsThem() {
        UserId admin = newAdmin();
        Community community = newCommunity();
        User owner = UserMother.randomUser();
        createUserRepository.create(owner);
        createMembershipRepository.create(community.getId(), owner.getId(), CommunityRole.COMMUNITY_MEMBER);
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

        Assertions.assertDoesNotThrow(() -> deleteUserService.delete(UserId.of(editor.getId())));

        Assertions.assertEquals(0, count("SELECT count(*) FROM users WHERE id = ?", editor.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM sharing_agreement WHERE id = ? AND created_by IS NULL "
                + "AND updated_by IS NULL", agreement.getId()));
        Assertions.assertEquals(1, count("SELECT count(*) FROM sharing_agreement_file WHERE id = ? "
                + "AND uploaded_by IS NULL", file.getId()));
        Assertions.assertNull(invitedBy(invited, community));
        Assertions.assertEquals(1, count("SELECT count(*) FROM supplies WHERE id = ?", supply.getId()));
    }

    private Community newCommunity() {
        return createCommunityRepository.create(CommunityMother.random().build());
    }

    /**
     * The user recorded as registering members: who registers them plays no part in what deleting someone else does.
     */
    private UserId newAdmin() {
        User admin = UserMother.randomUser();
        admin.setEnabled(true);
        createUserRepository.create(admin);
        return UserId.of(admin.getId());
    }

    private User newPlatformAdmin(boolean enabled) {
        User admin = UserMother.randomUser();
        admin.setEnabled(enabled);
        createUserRepository.create(admin);
        managePlatformAdminRepository.grant(UserId.of(admin.getId()));
        return admin;
    }

    private void register(Community community, UserId registeredBy, User user) {
        registerMemberService.register(community.getId(), registeredBy,
                UserPersonalId.of(user.getPersonalId()),
                new MemberRecord(user.getFullName(), null, "member-" + UUID.randomUUID() + "@example.org", null, null));
    }

    /**
     * Counts with plain SQL, after flushing the persistence context: the test's transaction is still open, and what
     * is asserted is what the database holds.
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
