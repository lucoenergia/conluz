package org.lucoenergia.conluz.infrastructure.shared.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.community.membership.CreateMembershipRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.production.sharingagreement.MaterializeSharingAgreementCoefficientsService;
import org.lucoenergia.conluz.domain.production.sharingagreement.ResolvedCoefficientEntry;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.domain.shared.UserPersonalId;
import org.lucoenergia.conluz.infrastructure.admin.community.MembershipStateTestSupport;
import org.lucoenergia.conluz.infrastructure.admin.community.CommunityMembershipJpaRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read endpoints of the main screens, answered with {@code spring.jpa.open-in-view} disabled (#480): each request
 * must load everything its response needs inside the transactions of the services it calls, since no session stays
 * open while the response is written.
 *
 * <p>Deliberately not {@code @Transactional}: a test transaction would wrap the request and keep a session open
 * through it, hiding a lazy load outside a transaction exactly as open-in-view did. The fixtures are created the way
 * production creates them, committed, and deleted afterwards.</p>
 */
class OpenInViewDisabledReadPathsControllerTest extends BaseControllerTest {

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CommunityMembershipJpaRepository membershipJpaRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private CreateSupplyService createSupplyService;
    @Autowired
    private CreatePlantService createPlantService;
    @Autowired
    private CreateSharingAgreementService createSharingAgreementService;
    @Autowired
    private MaterializeSharingAgreementCoefficientsService materializeService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> users = new CopyOnWriteArrayList<>();
    private Community community;
    private User communityAdmin;
    private User platformAdmin;
    private Supply supply;
    private Plant plant;
    private SharingAgreement agreement;

    @BeforeEach
    void createACommunityWithItsMembersSupplyPlantAndAgreement() {
        community = createCommunityRepository.create(CommunityMother.random().build());
        communityAdmin = newMemberOf(CommunityRole.COMMUNITY_ADMIN);
        User member = newMemberOf(CommunityRole.COMMUNITY_MEMBER);
        User invited = newMemberOf(CommunityRole.COMMUNITY_MEMBER);
        MembershipStateTestSupport.markPending(membershipJpaRepository, invited.getId(), community.getId());
        platformAdmin = newUser();
        managePlatformAdminRepository.grant(UserId.of(platformAdmin.getId()));

        supply = createSupplyService.create(SupplyMother.random(member).withCode("ES" + UUID.randomUUID()).build(),
                UserPersonalId.of(member.getPersonalId()), community.getId());
        plant = createPlantService.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        agreement = createSharingAgreementService.create(plant.getId(), new CreateSharingAgreement.Builder()
                .withName("Agreement " + UUID.randomUUID())
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedBy(communityAdmin.getId())
                .build());
        materializeService.replaceAllBySupplyId(plant.getId(), agreement.getId(),
                List.of(new ResolvedCoefficientEntry(supply.getId(), BigDecimal.ONE)));
    }

    @AfterEach
    void deleteTheData() {
        // Takes the agreement's coefficients with it
        jdbcTemplate.update("DELETE FROM sharing_agreement WHERE plant_id = ?", plant.getId());
        jdbcTemplate.update("DELETE FROM plants WHERE id = ?", plant.getId());
        jdbcTemplate.update("DELETE FROM supplies WHERE community_id = ?", community.getId());
        jdbcTemplate.update("DELETE FROM community_memberships WHERE community_id = ?", community.getId());
        for (UUID user : users) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", user);
        }
        jdbcTemplate.update("DELETE FROM communities WHERE id = ?", community.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/communities/{communityId}/memberships",
            "/api/v1/communities/{communityId}/supplies",
            "/api/v1/supplies/{supplyId}",
            "/api/v1/supplies/{supplyId}/partition-coefficients",
            "/api/v1/communities/{communityId}/plants",
            "/api/v1/plants/{plantId}",
            "/api/v1/plants/{plantId}/sharing-agreements",
            "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}",
            "/api/v1/plants/{plantId}/sharing-agreements/{sharingAgreementId}/partition-coefficients",
            "/api/v1/users",
            "/api/v1/users/current"
    })
    void aCommunityAdminReadsTheCommunityScreensWithoutAnOpenSession(String template) throws Exception {
        String bearer = loginUser(communityAdmin);

        mockMvc.perform(get(url(template)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/platform-admins",
            "/api/v1/communities/{communityId}/admins",
            "/api/v1/users/current"
    })
    void aPlatformAdminReadsThePlatformScreensWithoutAnOpenSession(String template) throws Exception {
        String bearer = loginUser(platformAdmin);

        mockMvc.perform(get(url(template)).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    private String url(String template) {
        return template
                .replace("{communityId}", community.getId().toString())
                .replace("{supplyId}", supply.getId().toString())
                .replace("{plantId}", plant.getId().toString())
                .replace("{sharingAgreementId}", agreement.getId().toString());
    }

    private User newMemberOf(CommunityRole role) {
        User user = newUser();
        createMembershipRepository.create(community.getId(), user.getId(), role);
        return user;
    }

    private User newUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        users.add(user.getId());
        return user;
    }
}
