package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.db.LockWaitTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Two plants created at the same time with the same CIL (PLT-005, AC9). The first creation holds its transaction open
 * with its row written; the second, on another supply of the same community, does not see that row, so it passes the
 * check before the write and then waits on the {@code plants_cil_uq} unique index until the first commits. The plant
 * creation locks only its own supply, against deletion, so the two do not wait for each other before that. Commits,
 * since the race is between two transactions; everything created is deleted afterwards.
 */
class CreatePlantCilRaceControllerTest extends BaseControllerTest {

    private static final String CIL = "ES0031406912345678AB1F001";

    @Autowired
    private CreatePlantService createPlantService;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private final List<UUID> communities = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteTheData() {
        for (UUID community : communities) {
            jdbcTemplate.update("DELETE FROM plants WHERE supply_id IN (SELECT id FROM supplies WHERE community_id = ?)",
                    community);
            jdbcTemplate.update("DELETE FROM supplies WHERE community_id = ?", community);
            jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", community);
        }
        for (UUID user : users) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
        for (UUID community : communities) {
            jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community);
        }
    }

    @Test
    @DisplayName("PLT-005 AC9 two plants created at the same time with the same CIL end with one holding it, and the "
            + "other refused with 409 PLANT_CIL_ALREADY_IN_USE")
    void twoConcurrentCreationsWithTheSameCilEndWithOnePlantHoldingIt() throws Exception {
        Community community = createCommunityRepository.create(CommunityMother.random().build());
        communities.add(community.getId());
        String admin = loginUser(newUser(community, CommunityRole.COMMUNITY_ADMIN));
        Supply first = newSupply(community);
        Supply second = newSupply(community);
        AtomicReference<MockHttpServletResponse> secondResponse = new AtomicReference<>();

        Throwable secondFailure = LockWaitTestSupport.runWhileHeld(transactionManager, jdbcTemplate,
                () -> createPlantService.create(PlantMother.random(first).withProviderCode("PS-RACE-1").withCil(CIL)
                        .build(), SupplyId.of(first.getId())),
                () -> secondResponse.set(createPlant(admin, second, "PS-RACE-2", CIL.toLowerCase())));

        assertThat(secondFailure).isNull();
        assertThat(secondResponse.get().getStatus()).isEqualTo(409);
        assertThat(secondResponse.get().getContentAsString())
                .contains("\"code\":\"PLANT_CIL_ALREADY_IN_USE\"")
                .doesNotContain("SUPPLY_ALREADY_HAS_PLANT");
        assertThat(jdbcTemplate.queryForList("SELECT provider_code FROM plants WHERE cil = ?", String.class, CIL))
                .containsExactly("PS-RACE-1");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM plants WHERE provider_code = 'PS-RACE-2'",
                Integer.class)).isZero();
    }

    private MockHttpServletResponse createPlant(String token, Supply supply, String providerCode, String cil) {
        try {
            Map<String, Object> body = Map.of("providerCode", providerCode, "name", "Plant " + providerCode,
                    "supplyCode", supply.getCode(), "address", "Fake Street 123", "inverterProvider", "HUAWEI",
                    "peakPowerKWp", 80.0, "nominalPowerKW", 60.0, "cil", cil);
            return mockMvc.perform(post("/api/v1/plants")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andReturn().getResponse();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private User newUser(Community community, CommunityRole role) {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        users.add(user.getId());
        createMembershipRepository.create(community.getId(), user.getId(), role);
        return user;
    }

    private Supply newSupply(Community community) {
        return createSupplyRepository.create(SupplyMother.random().build(),
                UserId.of(newUser(community, CommunityRole.COMMUNITY_MEMBER).getId()), community.getId());
    }
}
