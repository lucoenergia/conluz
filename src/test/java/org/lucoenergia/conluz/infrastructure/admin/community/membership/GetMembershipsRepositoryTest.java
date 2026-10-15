package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMembership;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityNotFoundException;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.GetMembershipsRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.UpdateMembershipRoleRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every read of a membership maps its member record and invitation state (#388), so a domain
 * membership never reports null for a value its row holds. The role update is included because it
 * returns the membership it wrote.
 */
@Transactional
class GetMembershipsRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private GetMembershipsRepository getMembershipsRepository;
    @Autowired
    private UpdateMembershipRoleRepository updateMembershipRoleRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MutableClock clock;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void everyReadMapsTheMemberRecordAndAcceptance() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUser();
        CommunityMembership created = createMembershipRepository.create(community.getId(), user.getId(),
                CommunityRole.COMMUNITY_MEMBER);

        assertEveryReadMatchesTheRow(created, community, user);
    }

    @Test
    void everyReadMapsTheInvitation() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUser();
        User inviter = createUser();
        CommunityMembership created = createMembershipRepository.create(community.getId(), user.getId(),
                CommunityRole.COMMUNITY_MEMBER);
        // No repository records an invitation yet, so the inviter is set on the entity directly
        CommunityMembershipEntity entity = membershipJpaRepository.findById(created.getId()).orElseThrow();
        entity.setInvitedBy(inviter.getId());
        entity.setInvitedAt(clock.instant().minus(Duration.ofDays(1)));
        membershipJpaRepository.saveAndFlush(entity);

        Map<String, Object> row = assertEveryReadMatchesTheRow(created, community, user);
        assertNotNull(row.get("invited_by"));
        assertNotNull(row.get("invited_at"));
    }

    @Test
    @DisplayName("MEM-021 the memberships of a role are those of that role only, in every state, by full name, with "
            + "their accounts")
    void findByCommunityIdAndRole_returnsOnlyThatRole() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        User zoe = createUser();
        User ana = createUser();
        User member = createUser();
        User elsewhere = createUser();
        createMembershipRepository.createPending(community.getId(), UserId.of(zoe.getId()),
                new MemberRecord("Zoe", null, "zoe@example.org", null, null), CommunityRole.COMMUNITY_ADMIN,
                UserId.of(member.getId()));
        createMembershipRepository.createPending(community.getId(), UserId.of(ana.getId()),
                new MemberRecord("Ana", null, "ana@example.org", null, null), CommunityRole.COMMUNITY_ADMIN,
                UserId.of(member.getId()));
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        createMembershipRepository.create(other.getId(), elsewhere.getId(), CommunityRole.COMMUNITY_ADMIN);
        entityManager.clear();

        List<CommunityMembership> admins = getMembershipsRepository.findByCommunityIdAndRole(community.getId(),
                CommunityRole.COMMUNITY_ADMIN);

        assertEquals(List.of("Ana", "Zoe"), admins.stream().map(CommunityMembership::getFullName).toList());
        assertEquals(List.of(ana.getPersonalId(), zoe.getPersonalId()),
                admins.stream().map(admin -> admin.getUser().getPersonalId()).toList());
    }

    @Test
    void findByCommunityIdAndRole_refusesAnUnknownCommunity() {
        assertThrows(CommunityNotFoundException.class, () -> getMembershipsRepository.findByCommunityIdAndRole(
                UUID.randomUUID(), CommunityRole.COMMUNITY_ADMIN));
    }

    private Map<String, Object> assertEveryReadMatchesTheRow(CommunityMembership created, Community community,
                                                             User user) {
        // Read from the database, not from the entities this test already holds
        membershipJpaRepository.flush();
        entityManager.clear();
        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM community_memberships WHERE id = ?",
                created.getId());

        assertMatchesRow(row, single(getMembershipsRepository.findByCommunityId(community.getId())));
        assertMatchesRow(row, single(getMembershipsRepository.findByUserId(user.getId())));
        assertMatchesRow(row, single(getMembershipsRepository.findByUserIds(List.of(user.getId())).get(user.getId())));
        assertMatchesRow(row, getMembershipsRepository.findByUserIdAndCommunityId(user.getId(), community.getId())
                .orElseThrow());
        assertMatchesRow(row, updateMembershipRoleRepository.updateRole(community.getId(), user.getId(),
                CommunityRole.COMMUNITY_ADMIN));
        assertMatchesRow(row, single(getMembershipsRepository.findByCommunityIdAndRole(community.getId(),
                CommunityRole.COMMUNITY_ADMIN)));
        return row;
    }

    private static void assertMatchesRow(Map<String, Object> row, CommunityMembership membership) {
        assertNotNull(row.get("accepted_at"));
        assertEquals(row.get("full_name"), membership.getFullName());
        assertEquals(row.get("member_number"), membership.getMemberNumber());
        assertEquals(row.get("contact_email"), membership.getContactEmail());
        assertEquals(row.get("address"), membership.getAddress());
        assertEquals(row.get("phone_number"), membership.getPhoneNumber());
        assertEquals(instant(row.get("accepted_at")), membership.getAcceptedAt());
        assertEquals(row.get("invited_by"), membership.getInvitedBy());
        assertEquals(instant(row.get("invited_at")), membership.getInvitedAt());
    }

    private static CommunityMembership single(List<CommunityMembership> memberships) {
        assertEquals(1, memberships.size());
        return memberships.get(0);
    }

    private static Instant instant(Object timestamp) {
        return timestamp == null ? null : ((Timestamp) timestamp).toInstant();
    }

    private User createUser() {
        User user = UserMother.randomUser();
        user.enable();
        return createUserRepository.create(user);
    }
}
