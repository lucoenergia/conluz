package org.lucoenergia.conluz.infrastructure.admin.supply.delete;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deleting a supply below the service layer, where SUP-001's plant check does not run (#438). Commits, since the
 * foreign key is checked when the deletion is flushed.
 */
class DeleteSupplyRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeleteSupplyRepository deleteSupplyRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private Community community;

    @AfterEach
    void deleteTheData() {
        if (owner != null) {
            jdbcTemplate.update("DELETE FROM plants WHERE supply_id IN (SELECT id FROM supplies WHERE user_id = ?)",
                    owner.getId());
            jdbcTemplate.update("DELETE FROM supplies WHERE user_id = ?", owner.getId());
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", owner.getId());
        }
        if (community != null) {
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community.getId());
        }
    }

    @Test
    @DisplayName("SUP-001 deleting a supply a plant is attached to, below the service layer, fails on the foreign key "
            + "and keeps the plant")
    void deletingASupplyWithAPlantFailsOnTheForeignKey() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        owner = createUserRepository.create(UserMother.randomUser());
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner).build(), UserId.of(owner.getId()),
                community.getId());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));

        assertThatThrownBy(() -> deleteSupplyRepository.delete(SupplyId.of(supply.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("plants_supply_id_fk");

        assertThat(count("SELECT count(*) FROM supplies WHERE id = ? AND user_id = ?", supply.getId(), owner.getId()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM supplies_contract WHERE supply_id = ?", supply.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM supplies_distributor WHERE supply_id = ?", supply.getId()))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM supplies_shelly WHERE supply_id = ?", supply.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM plants WHERE id = ? AND supply_id = ? AND provider_code = ?",
                plant.getId(), supply.getId(), plant.getProviderCode())).isEqualTo(1);
    }

    private int count(String sql, Object... parameters) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
    }
}
