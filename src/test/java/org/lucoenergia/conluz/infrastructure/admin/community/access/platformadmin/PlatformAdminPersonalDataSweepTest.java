package org.lucoenergia.conluz.infrastructure.admin.community.access.platformadmin;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.Community;
import org.lucoenergia.conluz.domain.admin.community.CommunityRole;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreement;
import org.lucoenergia.conluz.domain.production.sharingagreement.SharingAgreementStatus;
import org.lucoenergia.conluz.domain.production.sharingagreement.create.CreateSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.community.membership.register.BaseRegisterMemberTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * No response a platform admin without memberships can obtain carries a member's personal data (#446); admin data
 * appears only through {@code …/admins}.
 *
 * <p>Every {@code GET} of the OpenAPI document is called with the ids of an existing member, their supply, a plant
 * and a sharing agreement. A path parameter or a required query parameter the test does not know fails it, so a new
 * endpoint cannot slip past the sweep. Every value is a log secret as well.</p>
 */
class PlatformAdminPersonalDataSweepTest extends BaseRegisterMemberTest {

    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{(\\w+)}");

    /**
     * The required query parameters of the documented reads, with a valid value each.
     */
    private static final Map<String, String> QUERY_VALUES = Map.of(
            "startDate", "2026-01-01T00:00:00.000+01:00",
            "endDate", "2026-01-31T00:00:00.000+01:00",
            "timestamp", "2026-01-15T12:00:00Z");

    /**
     * Reads the platform admin must still be answered, so that the sweep cannot pass by refusing everything.
     */
    private static final Set<String> MUST_ANSWER = Set.of(
            "/api/v1/communities",
            "/api/v1/communities/{communityId}",
            "/api/v1/communities/{communityId}/admins",
            "/api/v1/platform-admins",
            "/api/v1/users/current");

    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private CreatePlantRepository createPlantRepository;
    @Autowired
    private CreateSharingAgreementRepository createSharingAgreementRepository;

    private final List<UUID> plants = new ArrayList<>();

    @AfterEach
    void deleteThePlantsAndTheirAgreements() {
        for (UUID plant : plants) {
            jdbcTemplate.update("DELETE FROM sharing_agreement WHERE plant_id = ?", plant);
            jdbcTemplate.update("DELETE FROM plants WHERE id = ?", plant);
        }
    }

    @Test
    @DisplayName("ACC-020 MEM-031 no read a platform admin without memberships can make returns a member's personal data, and admin data only through .../admins")
    void noReadReturnsAMembersPersonalData() throws Exception {
        Community community = newCommunity();
        User admin = personWithDistinctData(community, CommunityRole.COMMUNITY_ADMIN, "611");
        User member = personWithDistinctData(community, CommunityRole.COMMUNITY_MEMBER, "622");
        Supply supply = createSupplyRepository.create(SupplyMother.random(member).build(),
                UserId.of(member.getId()), community.getId());
        Plant plant = createPlantRepository.create(PlantMother.random(supply).build(), SupplyId.of(supply.getId()));
        plants.add(plant.getId());
        SharingAgreement agreement = createSharingAgreementRepository.create(new SharingAgreement.Builder()
                .withId(UUID.randomUUID())
                .withPlantId(plant.getId())
                .withName("Agreement " + UUID.randomUUID())
                .withStatus(SharingAgreementStatus.DRAFT)
                .withInstalledPowerKw(BigDecimal.TEN)
                .withCreatedAt(Instant.now())
                .withCreatedBy(admin.getId())
                .build());

        List<String> memberSecrets = secretsOf(member);
        memberSecrets.add(supply.getCode());
        memberSecrets.add(supply.getAddress());
        // The admin's name is the community's aggregated data (adminNames); the rest of an admin's personal data
        // only .../admins may carry
        List<String> adminSecrets = new ArrayList<>(secretsOf(admin));
        adminSecrets.remove(admin.getFullName());
        memberSecrets.forEach(this::remember);
        adminSecrets.forEach(this::remember);

        Map<String, String> pathValues = Map.of(
                "communityId", community.getId().toString(),
                "userId", member.getId().toString(),
                "supplyId", supply.getId().toString(),
                "plantId", plant.getId().toString(),
                "sharingAgreementId", agreement.getId().toString());
        String platformAdmin = loginAsPlatformAdmin();

        List<String> problems = new ArrayList<>();
        Set<String> answered = new TreeSet<>();
        for (Map.Entry<String, JsonNode> read : documentedReads()) {
            String path = read.getKey();
            MockHttpServletRequestBuilder request = get(expand(path, pathValues, problems));
            for (JsonNode parameter : read.getValue().path("parameters")) {
                if ("query".equals(parameter.path("in").asText()) && parameter.path("required").asBoolean()) {
                    String name = parameter.get("name").asText();
                    String value = QUERY_VALUES.get(name);
                    if (value == null) {
                        problems.add(path + ": unknown required query parameter " + name);
                    } else {
                        request.param(name, value);
                    }
                }
            }
            MockHttpServletResponse response = mockMvc.perform(request
                    .header(HttpHeaders.AUTHORIZATION, platformAdmin)).andReturn().getResponse();
            if (response.getStatus() / 100 != 2) {
                continue;
            }
            answered.add(path);
            String body = response.getContentAsString(StandardCharsets.UTF_8);
            List<String> forbidden = new ArrayList<>(memberSecrets);
            if (!path.endsWith("/admins")) {
                forbidden.addAll(adminSecrets);
            }
            for (String secret : forbidden) {
                if (body.contains(secret)) {
                    problems.add(path + " answered " + response.getStatus() + " with personal data");
                }
            }
        }

        assertThat(problems).isEmpty();
        assertThat(answered).containsAll(MUST_ANSWER);
        // The detection itself works: the admin list does carry the admin's personal ID
        String admins = mockMvc.perform(get("/api/v1/communities/{communityId}/admins", community.getId())
                .header(HttpHeaders.AUTHORIZATION, platformAdmin)).andReturn().getResponse().getContentAsString();
        assertThat(admins).contains(admin.getPersonalId());
    }

    private List<Map.Entry<String, JsonNode>> documentedReads() throws Exception {
        JsonNode document = objectMapper.readTree(mockMvc.perform(get("/api-docs")).andReturn().getResponse()
                .getContentAsString());
        List<Map.Entry<String, JsonNode>> reads = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> paths = document.get("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            if (path.getValue().has("get")) {
                reads.add(Map.entry(path.getKey(), path.getValue().get("get")));
            }
        }
        assertThat(reads).as("the documented reads").hasSizeGreaterThan(40);
        return reads;
    }

    private static String expand(String path, Map<String, String> values, List<String> problems) {
        Matcher matcher = PATH_PARAMETER.matcher(path);
        StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                problems.add(path + ": unknown path parameter " + matcher.group(1));
                value = UUID.randomUUID().toString();
            }
            matcher.appendReplacement(expanded, value);
        }
        matcher.appendTail(expanded);
        return expanded.toString();
    }

    /**
     * A person whose every personal value is unique to this test, so a match in a response can only be theirs. The
     * membership copies the account's data into the member record, as the existing creation paths do.
     */
    private User personWithDistinctData(Community community, CommunityRole role, String phonePrefix) {
        User user = UserMother.randomUser();
        user.enable();
        user.setPhoneNumber(phonePrefix + String.format("%06d", (int) (Math.random() * 1_000_000)));
        user.setNumber(800_000_000 + (int) (Math.random() * 99_999_999));
        String rawPassword = user.getPassword();
        createUserRepository.create(user);
        deleteAfterwards(user.getPersonalId());
        remember(rawPassword);
        createMembershipService.create(community.getId(), user.getId(), role);
        return user;
    }

    private static List<String> secretsOf(User user) {
        List<String> secrets = new ArrayList<>();
        secrets.add(user.getPersonalId());
        secrets.add(user.getFullName());
        secrets.add(user.getEmail());
        secrets.add(user.getPhoneNumber());
        secrets.add(user.getAddress());
        secrets.add(String.valueOf(user.getNumber()));
        return secrets;
    }
}
