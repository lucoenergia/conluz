package org.lucoenergia.conluz.infrastructure.admin.community.access.platformadmin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import org.lucoenergia.conluz.domain.admin.user.platformadmin.ManagePlatformAdminRepository;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * A platform admin with no membership is refused every endpoint that reaches a member's account, a community's
 * memberships, or a supply, plant or their data (#446), and the refusal writes nothing.
 *
 * <p>Each answer follows the guard conventions: a community the platform admin can see but where the action is
 * refused answers 403; an object it cannot see (a user, a supply, a plant, a membership's private figures) answers
 * 404. Every request carries a body that passes validation, so the answer is the guard's.</p>
 */
@Transactional
class PlatformAdminRefusedEndpointsControllerTest extends BaseControllerTest {

    private static final String START = "2026-01-01T00:00:00.000+01:00";
    private static final String END = "2026-01-31T00:00:00.000+01:00";

    /**
     * Every table a refused request could write to. Compared before and after each request.
     */
    private static final List<String> TABLES = List.of("users", "communities", "community_memberships", "supplies",
            "plants", "sharing_agreement", "sharing_agreement_file", "supply_partition_coefficient", "one_time_token",
            "datadis_config", "huawei_config", "shelly_config");

    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private CreateMembershipRepository createMembershipRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;
    @Autowired
    private ManagePlatformAdminRepository managePlatformAdminRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    private Community community;
    private User member;
    private Supply supply;
    private Plant plant;
    private SharingAgreement agreement;
    private String platformAdmin;

    @BeforeEach
    void createACommunityWithAMemberTheirSupplyAndAPlant() throws Exception {
        community = createCommunityRepository.create(CommunityMother.random().build());
        User admin = enabledUser();
        createMembershipRepository.create(community.getId(), admin.getId(), CommunityRole.COMMUNITY_ADMIN);
        member = enabledUser();
        createMembershipRepository.create(community.getId(), member.getId(), CommunityRole.COMMUNITY_MEMBER);
        supply = createSupplyRepository.create(SupplyMother.random(member).build(), UserId.of(member.getId()),
                community.getId());
        plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .withCreatedBy(admin.getId())
                .build());

        User platformAdminUser = enabledUser();
        managePlatformAdminRepository.grant(UserId.of(platformAdminUser.getId()));
        platformAdmin = loginUser(platformAdminUser);
    }

    @Test
    @DisplayName("ACC-020 a platform admin without memberships is refused every endpoint on another user's account")
    void everyUserEndpointIsRefused() throws Exception {
        assertEveryCallIsRefused(List.of(
                call(403, HttpMethod.GET, "/api/v1/users"),
                call(404, HttpMethod.GET, "/api/v1/users/{u}"),
                call(404, HttpMethod.GET, "/api/v1/users/{u}/supplies"),
                call(403, HttpMethod.POST, "/api/v1/users").body(newUserBody(true)),
                call(403, HttpMethod.POST, "/api/v1/users").body(newUserBody(false)),
                call(403, HttpMethod.POST, "/api/v1/users/import").file(usersCsv()).param("communityId", "{c}"),
                call(403, HttpMethod.POST, "/api/v1/users/import").file(usersCsv()),
                call(404, HttpMethod.PUT, "/api/v1/users/{u}").body(Map.of("personalId", member.getPersonalId(),
                        "fullName", "Taken Over", "number", 1, "email", "taken.over@example.org")),
                call(404, HttpMethod.DELETE, "/api/v1/users/{u}"),
                call(404, HttpMethod.POST, "/api/v1/users/{u}/enable"),
                call(404, HttpMethod.POST, "/api/v1/users/{u}/disable")));
    }

    @Test
    @DisplayName("MEM-031 a platform admin without memberships is refused every endpoint under .../memberships")
    void everyMembershipEndpointIsRefused() throws Exception {
        String memberships = "/api/v1/communities/{c}/memberships";
        assertEveryCallIsRefused(List.of(
                call(403, HttpMethod.GET, memberships),
                call(403, HttpMethod.POST, memberships).body(Map.of("userId", member.getId(), "role", "COMMUNITY_ADMIN")),
                call(403, HttpMethod.POST, memberships + "/import").file(membersCsv()),
                call(403, HttpMethod.POST, memberships + "/invite"),
                call(403, HttpMethod.POST, memberships + "/register").body(Map.of(
                        "personalId", UserMother.randomPersonalId(), "fullName", "Registered By Nobody")),
                call(403, HttpMethod.DELETE, memberships + "/{u}"),
                call(403, HttpMethod.PATCH, memberships + "/{u}").body(Map.of("role", "COMMUNITY_ADMIN")),
                call(403, HttpMethod.POST, memberships + "/{u}/disable"),
                call(403, HttpMethod.POST, memberships + "/{u}/enable"),
                call(403, HttpMethod.POST, memberships + "/{u}/invite"),
                call(403, HttpMethod.POST, memberships + "/{u}/invitation/resend"),
                call(403, HttpMethod.POST, memberships + "/{u}/invitation/revoke"),
                call(403, HttpMethod.PUT, memberships + "/{u}/record").body(Map.of("fullName", "Rewritten")),
                call(404, HttpMethod.PUT, memberships + "/{u}/investment").body(Map.of("investmentEur", 100)),
                call(404, HttpMethod.DELETE, memberships + "/{u}/investment"),
                call(404, HttpMethod.GET, memberships + "/{u}/payback"),
                call(404, HttpMethod.GET, memberships + "/{u}/consumption/monthly").dates(),
                call(404, HttpMethod.GET, memberships + "/{u}/energy-metrics"),
                call(404, HttpMethod.GET, memberships + "/{u}/energy-metrics/hourly-profile")));
    }

    @Test
    @DisplayName("a platform admin without memberships is refused every supply endpoint and every supply's data")
    void everySupplyEndpointIsRefused() throws Exception {
        String supplies = "/api/v1/supplies/{s}";
        assertEveryCallIsRefused(List.of(
                call(403, HttpMethod.GET, "/api/v1/communities/{c}/supplies"),
                call(403, HttpMethod.POST, "/api/v1/communities/{c}/supplies/datadis/sync"),
                call(403, HttpMethod.POST, "/api/v1/supplies").body(Map.of("code", "ES" + UUID.randomUUID(),
                        "address", "Calle Mayor 2", "communityId", community.getId(),
                        "personalId", member.getPersonalId())),
                call(403, HttpMethod.POST, "/api/v1/supplies/import").file(suppliesCsv()).param("communityId", "{c}"),
                call(404, HttpMethod.GET, supplies),
                call(404, HttpMethod.PUT, supplies).body(Map.of("code", supply.getCode(), "address", "Calle Mayor 3")),
                call(404, HttpMethod.DELETE, supplies),
                call(404, HttpMethod.POST, supplies + "/enable"),
                call(404, HttpMethod.POST, supplies + "/disable"),
                call(404, HttpMethod.GET, supplies + "/consumption/daily").dates(),
                call(404, HttpMethod.GET, supplies + "/consumption/hourly").dates(),
                call(404, HttpMethod.GET, supplies + "/consumption/monthly").dates(),
                call(404, HttpMethod.GET, supplies + "/consumption/yearly").dates(),
                call(404, HttpMethod.GET, supplies + "/production/daily").dates(),
                call(404, HttpMethod.GET, supplies + "/production/hourly").dates(),
                call(404, HttpMethod.GET, supplies + "/production/monthly").dates(),
                call(404, HttpMethod.GET, supplies + "/energy-metrics"),
                call(404, HttpMethod.GET, supplies + "/partition-coefficients"),
                call(404, HttpMethod.GET, supplies + "/partition-coefficients/active"),
                call(404, HttpMethod.GET, supplies + "/partition-coefficients/at").param("timestamp", "2026-01-15T12:00:00Z")));
    }

    @Test
    @DisplayName("a platform admin without memberships is refused every plant and sharing-agreement endpoint")
    void everyPlantAndSharingAgreementEndpointIsRefused() throws Exception {
        String plants = "/api/v1/plants/{p}";
        String agreements = plants + "/sharing-agreements";
        String anAgreement = agreements + "/{a}";
        Map<String, Object> plantBody = Map.of("name", "Plant", "address", "Calle Mayor 4", "supplyCode",
                supply.getCode(), "providerCode", "NE=1", "inverterProvider", "HUAWEI", "totalPower", 10.0);
        Map<String, Object> coefficientIds = Map.of("coefficientIds", List.of(UUID.randomUUID()));
        assertEveryCallIsRefused(List.of(
                call(403, HttpMethod.GET, "/api/v1/communities/{c}/plants"),
                call(404, HttpMethod.POST, "/api/v1/plants").body(plantBody),
                call(404, HttpMethod.GET, plants),
                call(404, HttpMethod.PUT, plants).body(plantBody),
                call(404, HttpMethod.DELETE, plants),
                call(404, HttpMethod.GET, plants + "/partition-coefficients/active"),
                call(404, HttpMethod.GET, plants + "/production/huawei/config"),
                call(404, HttpMethod.PUT, plants + "/production/huawei/config").body(Map.of(
                        "baseUrl", "https://huawei.test", "username", "user", "password", "secret")),
                call(404, HttpMethod.POST, plants + "/production/huawei/config/enable"),
                call(404, HttpMethod.POST, plants + "/production/huawei/config/disable"),
                call(404, HttpMethod.GET, agreements),
                call(404, HttpMethod.POST, agreements).body(Map.of("name", "Agreement", "installedPowerKw", 10)),
                call(404, HttpMethod.GET, anAgreement),
                call(404, HttpMethod.PUT, anAgreement).body(Map.of("name", "Agreement", "installedPowerKw", 10)),
                call(404, HttpMethod.DELETE, anAgreement),
                call(404, HttpMethod.POST, anAgreement + "/publish"),
                call(404, HttpMethod.POST, anAgreement + "/revert-to-draft"),
                call(404, HttpMethod.GET, anAgreement + "/file"),
                call(404, HttpMethod.POST, anAgreement + "/file").file(new MockMultipartFile("file", "RC_2026.txt",
                        MediaType.TEXT_PLAIN_VALUE, (supply.getCode() + ";1,0\n").getBytes(StandardCharsets.UTF_8))),
                call(404, HttpMethod.POST, anAgreement + "/generate-file").body(Map.of("year", 2026)),
                call(404, HttpMethod.GET, anAgreement + "/partition-coefficients"),
                call(404, HttpMethod.PUT, anAgreement + "/partition-coefficients").body(Map.of("coefficients",
                        List.of(Map.of("supplyId", supply.getId(), "coefficient", 1)))),
                call(404, HttpMethod.POST, anAgreement + "/partition-coefficients/activate").body(Map.of(
                        "appliedOn", "2026-01-01", "coefficientIds", List.of(UUID.randomUUID()))),
                call(404, HttpMethod.POST, anAgreement + "/partition-coefficients/close").body(Map.of(
                        "closedOn", "2026-01-01", "coefficientIds", List.of(UUID.randomUUID()))),
                call(404, HttpMethod.POST, anAgreement + "/partition-coefficients/deactivate").body(coefficientIds),
                call(404, HttpMethod.POST, anAgreement + "/partition-coefficients/reopen").body(coefficientIds)));
    }

    @Test
    @DisplayName("MEM-031 a platform admin without memberships is refused a community's production, consumption, "
            + "configuration and synchronisation")
    void everyCommunityDataEndpointIsRefused() throws Exception {
        String c = "/api/v1/communities/{c}";
        Map<String, Object> year = Map.of("year", 2026);
        Map<String, Object> credentials = Map.of("baseUrl", "https://datadis.test", "username", "user",
                "password", "secret");
        assertEveryCallIsRefused(List.of(
                call(403, HttpMethod.GET, c + "/production"),
                call(403, HttpMethod.GET, c + "/production/daily").dates(),
                call(403, HttpMethod.GET, c + "/production/hourly").dates(),
                call(403, HttpMethod.GET, c + "/production/monthly").dates(),
                call(403, HttpMethod.GET, c + "/production/yearly").dates(),
                call(403, HttpMethod.GET, c + "/production/datadis/daily").dates(),
                call(403, HttpMethod.GET, c + "/production/datadis/hourly").dates(),
                call(403, HttpMethod.GET, c + "/production/datadis/monthly").dates(),
                call(403, HttpMethod.GET, c + "/production/datadis/yearly").dates(),
                call(403, HttpMethod.POST, c + "/production/datadis/sync/monthly").body(year),
                call(403, HttpMethod.POST, c + "/production/datadis/sync/yearly").body(year),
                call(403, HttpMethod.POST, c + "/production/huawei/sync/monthly").body(year),
                call(403, HttpMethod.POST, c + "/production/huawei/sync/yearly").body(year),
                call(403, HttpMethod.GET, c + "/consumption/datadis/report/hourly/csv").dates(),
                call(403, HttpMethod.POST, c + "/consumption/datadis/sync").body(year),
                call(403, HttpMethod.POST, c + "/consumption/datadis/sync/monthly").body(year),
                call(403, HttpMethod.POST, c + "/consumption/datadis/sync/yearly").body(year),
                call(403, HttpMethod.GET, c + "/config/datadis"),
                call(403, HttpMethod.PUT, c + "/config/datadis").body(credentials),
                call(403, HttpMethod.POST, c + "/config/datadis/enable"),
                call(403, HttpMethod.POST, c + "/config/datadis/disable"),
                call(403, HttpMethod.GET, c + "/config/shelly"),
                call(403, HttpMethod.PUT, c + "/config/shelly").body(Map.of("enabled", true))));
    }

    // --- Running the calls ---

    private void assertEveryCallIsRefused(List<Call> calls) throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Call call : calls) {
            Map<String, String> before = fingerprint();
            int status = mockMvc.perform(call.build(this)).andReturn().getResponse().getStatus();
            if (status != call.expected) {
                wrong.add(call + " answered " + status);
            }
            if (!before.equals(fingerprint())) {
                wrong.add(call + " wrote to the database");
            }
        }
        assertThat(wrong).isEmpty();
    }

    /**
     * One digest per table, over every row's text, so that any write a refused request made shows.
     */
    private Map<String, String> fingerprint() {
        entityManager.flush();
        Map<String, String> digests = new LinkedHashMap<>();
        for (String table : TABLES) {
            digests.put(table, jdbcTemplate.queryForObject("SELECT md5(coalesce(string_agg(t::text, '|' "
                    + "ORDER BY t::text), '')) FROM " + table + " t", String.class));
        }
        return digests;
    }

    private String expand(String path) {
        return path.replace("{c}", community.getId().toString())
                .replace("{u}", member.getId().toString())
                .replace("{s}", supply.getId().toString())
                .replace("{p}", plant.getId().toString())
                .replace("{a}", agreement.getId().toString());
    }

    private static Call call(int expected, HttpMethod method, String path) {
        return new Call(expected, method, path);
    }

    private static final class Call {
        private final int expected;
        private final HttpMethod method;
        private final String path;
        private final Map<String, String> params = new LinkedHashMap<>();
        private Object body;
        private MockMultipartFile file;

        private Call(int expected, HttpMethod method, String path) {
            this.expected = expected;
            this.method = method;
            this.path = path;
        }

        Call body(Object body) {
            this.body = body;
            return this;
        }

        Call file(MockMultipartFile file) {
            this.file = file;
            return this;
        }

        Call param(String name, String value) {
            params.put(name, value);
            return this;
        }

        Call dates() {
            return param("startDate", START).param("endDate", END);
        }

        MockHttpServletRequestBuilder build(PlatformAdminRefusedEndpointsControllerTest test) throws Exception {
            String uri = test.expand(path);
            MockHttpServletRequestBuilder request = file != null
                    ? multipart(method, uri).file(file)
                    : request(method, uri);
            params.forEach((name, value) -> request.param(name, test.expand(value)));
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).content(test.objectMapper.writeValueAsString(body));
            }
            return request.header(HttpHeaders.AUTHORIZATION, test.platformAdmin);
        }

        @Override
        public String toString() {
            return method + " " + path + (params.isEmpty() ? "" : " " + params.keySet());
        }
    }

    // --- Fixtures ---

    private User enabledUser() {
        User user = UserMother.randomUser();
        user.enable();
        createUserRepository.create(user);
        return user;
    }

    private Map<String, Object> newUserBody(boolean inTheCommunity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personalId", UserMother.randomPersonalId());
        body.put("fullName", "Created By Nobody");
        body.put("number", 7);
        body.put("email", "created-" + UUID.randomUUID() + "@example.org");
        body.put("password", "a secure password1!");
        if (inTheCommunity) {
            body.put("communityId", community.getId());
            body.put("communityRole", "COMMUNITY_ADMIN");
        }
        return body;
    }

    private static MockMultipartFile usersCsv() {
        return csv("number,fullName,personalId,address,email,phoneNumber,role,password,communityId,communityRole\n"
                + "1,Imported By Nobody," + UserMother.randomPersonalId() + ",1 Test St,imported@example.org,"
                + "600000001,partner,a secure password1!,,COMMUNITY_ADMIN\n");
    }

    private static MockMultipartFile membersCsv() {
        return csv("personalId,fullName\n" + UserMother.randomPersonalId() + ",Imported By Nobody\n");
    }

    private MockMultipartFile suppliesCsv() {
        return csv("code,address,addressRef,personalId\nES" + UUID.randomUUID() + ",Calle Mayor 5,,"
                + member.getPersonalId() + "\n");
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "rows.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
