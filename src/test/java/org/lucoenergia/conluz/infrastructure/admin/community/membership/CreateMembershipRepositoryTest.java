package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.MembershipAlreadyExistsException;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipEntity;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class CreateMembershipRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private DeleteUserRepository deleteUserRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("the membership holds a copy of the user's record and is accepted at the creation instant")
    void create_copiesTheUsersRecord_andAcceptsTheMembership() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUser("member@example.org");
        Instant now = clock.instant();

        CommunityMembership created = createMembershipRepository.create(community.getId(), user.getId(),
                CommunityRole.COMMUNITY_MEMBER);

        Map<String, Object> row = row(created);
        assertEquals(user.getFullName(), row.get("full_name"));
        assertEquals(user.getNumber(), row.get("member_number"));
        assertEquals("member@example.org", row.get("contact_email"));
        assertEquals(user.getAddress(), row.get("address"));
        assertEquals(user.getPhoneNumber(), row.get("phone_number"));
        assertEquals(now, ((Timestamp) row.get("accepted_at")).toInstant());
        assertNull(row.get("invited_by"));
        assertNull(row.get("invited_at"));

        assertEquals(user.getFullName(), created.getFullName());
        assertEquals(user.getNumber(), created.getMemberNumber());
        assertEquals("member@example.org", created.getContactEmail());
        assertEquals(user.getAddress(), created.getAddress());
        assertEquals(user.getPhoneNumber(), created.getPhoneNumber());
        assertEquals(now, created.getAcceptedAt());
        assertNull(created.getInvitedBy());
        assertNull(created.getInvitedAt());
    }

    @Test
    @DisplayName("a user's empty or blank email is recorded as no contact email")
    void create_recordsNoContactEmail_whenTheUsersEmailIsEmptyOrBlank() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User withEmptyEmail = createUser("");
        User withBlankEmail = createUser(" \t ");

        CommunityMembership empty = createMembershipRepository.create(community.getId(), withEmptyEmail.getId(),
                CommunityRole.COMMUNITY_MEMBER);
        CommunityMembership blank = createMembershipRepository.create(community.getId(), withBlankEmail.getId(),
                CommunityRole.COMMUNITY_MEMBER);

        assertNull(row(empty).get("contact_email"));
        assertNull(row(blank).get("contact_email"));
        assertNull(empty.getContactEmail());
        assertNull(blank.getContactEmail());
    }

    @Test
    @DisplayName("MEM-003 deleting the user who invited a membership keeps the membership, with no inviter recorded")
    void deletingTheInvitingUser_keepsTheMembership_withNoInviter() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = createUser("member@example.org");
        User inviter = createUser("inviter@example.org");
        CommunityMembership created = createMembershipRepository.create(community.getId(), member.getId(),
                CommunityRole.COMMUNITY_MEMBER);
        Instant invitedAt = clock.instant().minus(Duration.ofDays(1));
        // No repository records an invitation yet, so the inviter is set on the entity directly
        CommunityMembershipEntity entity = membershipJpaRepository.findById(created.getId()).orElseThrow();
        entity.setInvitedBy(inviter.getId());
        entity.setInvitedAt(invitedAt);
        membershipJpaRepository.saveAndFlush(entity);
        assertEquals(inviter.getId(), row(created).get("invited_by"));

        deleteUserRepository.delete(UserId.of(inviter.getId()));
        membershipJpaRepository.flush();

        Map<String, Object> row = row(created);
        assertNull(row.get("invited_by"));
        assertEquals(invitedAt, ((Timestamp) row.get("invited_at")).toInstant());
        assertEquals(member.getId(), row.get("user_id"));
        assertNotNull(row.get("accepted_at"));
    }

    @Test
    @DisplayName("MEM-007 a pending membership holds the record as given, the inviter, and no acceptance or invitation")
    void createPending_holdsTheGivenRecord_andIsPending() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = createUser("account@example.org");
        User inviter = createUser("inviter@example.org");
        MemberRecord record = new MemberRecord("Lucía Pérez", 7, "contact@example.org", "Calle Mayor 1",
                "600000000");

        CommunityMembership created = createMembershipRepository.createPending(community.getId(),
                UserId.of(member.getId()), record, CommunityRole.COMMUNITY_MEMBER, UserId.of(inviter.getId()));

        Map<String, Object> row = row(created);
        assertEquals(member.getId(), row.get("user_id"));
        assertEquals("COMMUNITY_MEMBER", row.get("role"));
        assertEquals(true, row.get("enabled"));
        assertEquals("Lucía Pérez", row.get("full_name"));
        assertEquals(7, row.get("member_number"));
        assertEquals("contact@example.org", row.get("contact_email"));
        assertEquals("Calle Mayor 1", row.get("address"));
        assertEquals("600000000", row.get("phone_number"));
        assertNull(row.get("accepted_at"));
        assertEquals(inviter.getId(), row.get("invited_by"));
        assertNull(row.get("invited_at"));

        assertTrue(created.isPending());
        assertEquals(record.fullName(), created.getFullName());
        assertEquals(record.contactEmail(), created.getContactEmail());
        assertEquals(inviter.getId(), created.getInvitedBy());
    }

    @Test
    @DisplayName("MEM-020 a pending membership holds the role it is created with")
    void createPending_holdsTheGivenRole() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User admin = createUser("account@example.org");
        User inviter = createUser("inviter@example.org");
        MemberRecord record = new MemberRecord("Lucía Pérez", null, "contact@example.org", null, null);

        CommunityMembership created = createMembershipRepository.createPending(community.getId(),
                UserId.of(admin.getId()), record, CommunityRole.COMMUNITY_ADMIN, UserId.of(inviter.getId()));

        assertEquals("COMMUNITY_ADMIN", row(created).get("role"));
        assertEquals(CommunityRole.COMMUNITY_ADMIN, created.getRole());
        assertTrue(created.isPending());
    }

    @Test
    @DisplayName("MEM-007 a pending membership is refused when the user already has a membership in the community")
    void createPending_isRefused_whenTheUserAlreadyHasAMembershipThere() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = createUser("account@example.org");
        User inviter = createUser("inviter@example.org");
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        MemberRecord record = new MemberRecord("Lucía Pérez", null, null, null, null);

        assertThrows(MembershipAlreadyExistsException.class, () -> createMembershipRepository.createPending(
                community.getId(), UserId.of(member.getId()), record, CommunityRole.COMMUNITY_MEMBER,
                UserId.of(inviter.getId())));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM community_memberships WHERE user_id = ?",
                Integer.class, member.getId()));
    }

    private User createUser(String email) {
        User user = UserMother.randomUser();
        user.setEmail(email);
        user.enable();
        return createUserRepository.create(user);
    }

    private Map<String, Object> row(CommunityMembership membership) {
        membershipJpaRepository.flush();
        return jdbcTemplate.queryForMap("SELECT * FROM community_memberships WHERE id = ?", membership.getId());
    }
}
