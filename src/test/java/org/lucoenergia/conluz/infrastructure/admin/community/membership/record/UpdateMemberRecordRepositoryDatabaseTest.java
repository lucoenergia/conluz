package org.lucoenergia.conluz.infrastructure.admin.community.membership.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.MemberRecord;
import org.lucoenergia.conluz.domain.admin.community.membership.record.UpdateMemberRecordRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class UpdateMemberRecordRepositoryDatabaseTest extends BaseIntegrationTest {

    private static final List<String> RECORD_COLUMNS =
            List.of("full_name", "member_number", "contact_email", "address", "phone_number");

    @Autowired
    private UpdateMemberRecordRepository updateMemberRecordRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("MEM-028 only the five record columns of that membership are written, never the account or another "
            + "membership")
    void writesOnlyTheRecordOfThatMembership() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        User inviter = createUser();
        User user = createUser();
        createMembershipRepository.createPending(community.getId(), UserId.of(user.getId()),
                new MemberRecord("Old Name", 1, "old@example.org", "Old address", "600000001"),
                CommunityRole.COMMUNITY_MEMBER, UserId.of(inviter.getId()));
        createMembershipRepository.createPending(other.getId(), UserId.of(user.getId()),
                new MemberRecord("Other Name", 2, "other@example.org", "Other address", "600000002"),
                CommunityRole.COMMUNITY_ADMIN, UserId.of(inviter.getId()));
        Map<String, Object> membershipBefore = membershipRow(user.getId(), community.getId());
        Map<String, Object> otherBefore = membershipRow(user.getId(), other.getId());
        Map<String, Object> accountBefore = accountRow(user.getId());

        boolean written = updateMemberRecordRepository.update(community.getId(), UserId.of(user.getId()),
                new MemberRecord("New Name", 3, "new@example.org", "New address", "600000003"));

        assertThat(written).isTrue();
        Map<String, Object> membershipAfter = membershipRow(user.getId(), community.getId());
        assertThat(membershipAfter)
                .containsEntry("full_name", "New Name")
                .containsEntry("member_number", 3)
                .containsEntry("contact_email", "new@example.org")
                .containsEntry("address", "New address")
                .containsEntry("phone_number", "600000003");
        assertThat(withoutRecord(membershipAfter)).isEqualTo(withoutRecord(membershipBefore));
        assertThat(membershipRow(user.getId(), other.getId())).isEqualTo(otherBefore);
        assertThat(accountRow(user.getId())).isEqualTo(accountBefore);
    }

    @Test
    @DisplayName("MEM-028 an empty optional value is stored as none")
    void emptyOptionalValues_areStoredAsNull() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUser();
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_MEMBER);

        updateMemberRecordRepository.update(community.getId(), UserId.of(user.getId()),
                new MemberRecord("Only A Name", null, null, null, null));

        Map<String, Object> row = membershipRow(user.getId(), community.getId());
        assertThat(row).containsEntry("full_name", "Only A Name");
        assertThat(row.get("member_number")).isNull();
        assertThat(row.get("contact_email")).isNull();
        assertThat(row.get("address")).isNull();
        assertThat(row.get("phone_number")).isNull();
    }

    @Test
    @DisplayName("MEM-028 a user without a membership in the community is not found, and nothing is written")
    void aUserWithoutAMembershipThere_isNotFound() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        User inviter = createUser();
        User user = createUser();
        createMembershipRepository.createPending(other.getId(), UserId.of(user.getId()),
                new MemberRecord("Other Name", 2, "other@example.org", "Other address", "600000002"),
                CommunityRole.COMMUNITY_MEMBER, UserId.of(inviter.getId()));
        Map<String, Object> otherBefore = membershipRow(user.getId(), other.getId());

        assertThat(updateMemberRecordRepository.update(community.getId(), UserId.of(user.getId()),
                new MemberRecord("New Name", null, null, null, null))).isFalse();
        assertThat(updateMemberRecordRepository.update(community.getId(), UserId.of(UUID.randomUUID()),
                new MemberRecord("New Name", null, null, null, null))).isFalse();

        assertThat(membershipRow(user.getId(), other.getId())).isEqualTo(otherBefore);
    }

    private Map<String, Object> membershipRow(UUID userId, UUID communityId) {
        return jdbcTemplate.queryForMap("SELECT * FROM community_memberships WHERE user_id = ? AND community_id = ?",
                userId, communityId);
    }

    private Map<String, Object> accountRow(UUID userId) {
        return jdbcTemplate.queryForMap("SELECT * FROM users WHERE id = ?", userId);
    }

    private static Map<String, Object> withoutRecord(Map<String, Object> row) {
        Map<String, Object> rest = new HashMap<>(row);
        RECORD_COLUMNS.forEach(rest::remove);
        return rest;
    }

    private User createUser() {
        User user = UserMother.randomUser();
        user.enable();
        return createUserRepository.create(user);
    }
}
