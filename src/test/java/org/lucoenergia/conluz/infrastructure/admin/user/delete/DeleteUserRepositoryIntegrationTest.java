package org.lucoenergia.conluz.infrastructure.admin.user.delete;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.delete.DeleteUserRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deleting a user below the service layer, where ACC-009's supply check does not run (#438). Commits, since the
 * foreign key is checked when the deletion is flushed. The supplies are created through the repository, which does
 * not require a membership, so the supplies are the only rows that reference the user.
 */
class DeleteUserRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeleteUserRepository deleteUserRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private Community community;

    @AfterEach
    void deleteTheData() {
        if (owner != null) {
            jdbcTemplate.update("DELETE FROM supplies WHERE user_id = ?", owner.getId());
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", owner.getId());
        }
        if (community != null) {
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community.getId());
        }
    }

    @Test
    @DisplayName("ACC-009 deleting a user who owns supplies, below the service layer, fails on the foreign key and "
            + "keeps the supplies")
    void deletingASupplyOwnerFailsOnTheForeignKey() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        owner = createUserRepository.create(UserMother.randomUser());
        Supply first = createSupply();
        Supply second = createSupply();

        assertThatThrownBy(() -> deleteUserRepository.delete(UserId.of(owner.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("supplies_user_id_fk");

        assertThat(count("SELECT count(*) FROM users WHERE id = ?", owner.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("SELECT id FROM supplies WHERE user_id = ? AND community_id = ?",
                UUID.class, owner.getId(), community.getId()))
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        for (UUID supply : List.of(first.getId(), second.getId())) {
            assertThat(count("SELECT count(*) FROM supplies_contract WHERE supply_id = ?", supply)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM supplies_distributor WHERE supply_id = ?", supply)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM supplies_shelly WHERE supply_id = ?", supply)).isEqualTo(1);
        }
    }

    private Supply createSupply() {
        return createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
    }

    private int count(String sql, Object... parameters) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
    }
}
