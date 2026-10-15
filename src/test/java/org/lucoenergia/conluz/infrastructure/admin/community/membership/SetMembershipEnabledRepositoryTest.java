package org.lucoenergia.conluz.infrastructure.admin.community.membership;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.SetMembershipEnabledRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class SetMembershipEnabledRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private SetMembershipEnabledRepository setMembershipEnabledRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("MEM-023 only the membership with the given role is written, and never the account")
    void writesOnlyTheMembershipOfThatRole() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        Community other = createCommunityRepository.create(CommunityMother.random().build());
        User user = createUser();
        createMembershipRepository.create(community.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);
        createMembershipRepository.create(other.getId(), user.getId(), CommunityRole.COMMUNITY_ADMIN);

        assertTrue(setMembershipEnabledRepository.setEnabled(community.getId(), UserId.of(user.getId()),
                CommunityRole.COMMUNITY_ADMIN, false));

        assertFalse(enabled(user.getId(), community.getId()));
        assertTrue(enabled(user.getId(), other.getId()));
        assertTrue(jdbcTemplate.queryForObject("SELECT enabled FROM users WHERE id = ?", Boolean.class,
                user.getId()));

        assertTrue(setMembershipEnabledRepository.setEnabled(community.getId(), UserId.of(user.getId()),
                CommunityRole.COMMUNITY_ADMIN, true));
        assertTrue(enabled(user.getId(), community.getId()));
    }

    @Test
    @DisplayName("MEM-023 a membership of another role, or none, is not found and not written")
    void anotherRole_isNotFound() {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        User member = createUser();
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);

        assertFalse(setMembershipEnabledRepository.setEnabled(community.getId(), UserId.of(member.getId()),
                CommunityRole.COMMUNITY_ADMIN, false));
        assertFalse(setMembershipEnabledRepository.setEnabled(community.getId(), UserId.of(UUID.randomUUID()),
                CommunityRole.COMMUNITY_ADMIN, false));

        assertTrue(enabled(member.getId(), community.getId()));
    }

    private boolean enabled(UUID userId, UUID communityId) {
        return jdbcTemplate.queryForObject("SELECT enabled FROM community_memberships WHERE user_id = ? "
                + "AND community_id = ?", Boolean.class, userId, communityId);
    }

    private User createUser() {
        User user = UserMother.randomUser();
        user.enable();
        return createUserRepository.create(user);
    }
}
