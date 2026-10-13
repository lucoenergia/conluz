package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
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
    @DisplayName("MEM-001 MEM-002 the membership holds a copy of the user's record and is accepted at the creation instant")
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
    @DisplayName("MEM-002 a user's empty or blank email is recorded as no contact email")
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
