package org.lucoenergia.conluz.infrastructure.datadis.sync;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisStub;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Datadis consumption sync end to end, against two communities whose Datadis accounts live at different base URLs
 * with different credentials. Only the network is replaced, by {@link DatadisStub}, which answers every consumption
 * request with no records, so nothing is written to InfluxDB.
 */
class DatadisConsumptionSyncIntegrationTest extends BaseControllerTest {

    private static final String BASE_URL_A = "http://datadis-a.test";
    private static final String BASE_URL_B = "http://datadis-b.test";
    private static final LocalDate OCTOBER = LocalDate.of(2025, 10, 1);
    private static final LocalDate NOVEMBER = LocalDate.of(2025, 11, 1);

    @Autowired
    private CreateCommunityRepository createCommunityRepository;
    @Autowired
    private SetDatadisConfigurationRepository setDatadisConfigurationRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;

    private final DatadisStub datadis = new DatadisStub();
    private final List<UUID> communitiesWithDatadis = new ArrayList<>();

    @BeforeEach
    void beforeEach() {
        doAnswer(invocation -> datadis.client()).when(conluzRestClientBuilder).build();
        doAnswer(invocation -> datadis.client()).when(conluzRestClientBuilder).build(anyBoolean(), any(Duration.class));
    }

    /**
     * The data is committed, so the configurations are disabled for the jobs that other tests run over every enabled
     * one.
     */
    @AfterEach
    void afterEach() {
        communitiesWithDatadis.forEach(communityId -> setDatadisConfigurationRepository.setEnabled(communityId, false));
    }

    @Test
    @DisplayName("DHI-005 each community logs in once per run, and every consumption request carries its own "
            + "community's token, across supplies and months")
    void eachCommunityLogsInOncePerRun() {
        UUID communityA = communityWithDatadis(BASE_URL_A, "token-a");
        UUID communityB = communityWithDatadis(BASE_URL_B, "token-b");
        User owner = owner();
        Supply a1 = supply(owner, communityA);
        supply(owner, communityA);
        supply(owner, communityB);
        supply(owner, communityB);

        datadisSyncService.synchronize(communityA, OCTOBER, NOVEMBER);
        datadisSyncService.synchronize(communityB, OCTOBER, NOVEMBER);

        assertThat(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH)).hasSize(1);
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.LOGIN_PATH)).hasSize(1);
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.CONSUMPTION_PATH))
                .hasSize(4)
                .allSatisfy(call -> {
                    assertThat(call.authorization()).isEqualTo("Bearer token-a");
                    assertThat(call.status()).isEqualTo(200);
                });
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.CONSUMPTION_PATH))
                .hasSize(4)
                .allSatisfy(call -> {
                    assertThat(call.authorization()).isEqualTo("Bearer token-b");
                    assertThat(call.status()).isEqualTo(200);
                });

        // The sync of one supply logs in once too, for both its months
        datadis.clear();
        datadisSyncService.synchronize(communityA, OCTOBER, NOVEMBER, a1.getCode());

        assertThat(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH)).hasSize(1);
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.CONSUMPTION_PATH))
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.cups()).isEqualTo(a1.getCode()));
    }

    @Test
    @DisplayName("DHI-005 when Datadis rejects the token once, the community logs in once more, the request is "
            + "retried, and the rest of the run uses the new token")
    void aRejectedTokenIsRenewedOnce() {
        UUID communityA = communityWithDatadis(BASE_URL_A, "token-a1", "token-a2");
        UUID communityB = communityWithDatadis(BASE_URL_B, "token-b");
        User owner = owner();
        supply(owner, communityA);
        supply(owner, communityA);
        supply(owner, communityB);
        supply(owner, communityB);
        datadis.expireFirstTokenAfter(BASE_URL_A, 1);

        datadisSyncService.synchronize(communityA, OCTOBER, NOVEMBER);
        datadisSyncService.synchronize(communityB, OCTOBER, NOVEMBER);

        assertThat(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH)).hasSize(2);
        List<DatadisStub.Call> calls = datadis.calls(BASE_URL_A, DatadisStub.CONSUMPTION_PATH);
        assertThat(calls).hasSize(5);
        assertThat(calls.get(0).authorization()).isEqualTo("Bearer token-a1");
        assertThat(calls.get(0).status()).isEqualTo(200);
        DatadisStub.Call rejected = calls.get(1);
        assertThat(rejected.authorization()).isEqualTo("Bearer token-a1");
        assertThat(rejected.status()).isEqualTo(401);
        DatadisStub.Call retried = calls.get(2);
        assertThat(retried.cups()).isEqualTo(rejected.cups());
        assertThat(retried.startDate()).isEqualTo(rejected.startDate());
        assertThat(calls.subList(2, 5)).allSatisfy(call -> {
            assertThat(call.authorization()).isEqualTo("Bearer token-a2");
            assertThat(call.status()).isEqualTo(200);
        });
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.LOGIN_PATH)).hasSize(1);
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.CONSUMPTION_PATH))
                .hasSize(4)
                .allSatisfy(call -> assertThat(call.status()).isEqualTo(200));
    }

    @Test
    @DisplayName("DHI-005 when the retry is rejected too, that month of that supply fails with no further login, and "
            + "the other supplies are still synced")
    void aSecondRejectionFailsThatSupplyMonth() {
        UUID communityA = communityWithDatadis(BASE_URL_A, "token-a1", "token-a2", "token-a3");
        UUID communityB = communityWithDatadis(BASE_URL_B, "token-b");
        User owner = owner();
        Supply refused = supply(owner, communityA);
        Supply other = supply(owner, communityA);
        supply(owner, communityB);
        supply(owner, communityB);
        datadis.rejectConsumption(BASE_URL_A, refused.getCode(), "2025/10");

        try (LogCapture logs = LogCapture.of(DatadisSyncServiceImpl.class)) {
            datadisSyncService.synchronize(communityA, OCTOBER, NOVEMBER);
            datadisSyncService.synchronize(communityB, OCTOBER, NOVEMBER);

            assertThat(logs.warningsAndAbove())
                    .filteredOn(event -> event.getLevel() == Level.ERROR)
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getMessage()).startsWith("Unable to retrieve hourly consumptions of supply");
                        assertThat(event.getArgumentArray()).startsWith(refused.getId(), 10, 2025);
                    });
        }
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH)).hasSize(2);
        List<DatadisStub.Call> calls = datadis.calls(BASE_URL_A, DatadisStub.CONSUMPTION_PATH);
        assertThat(calls)
                .filteredOn(call -> call.cups().equals(refused.getCode()) && call.startDate().equals("2025/10"))
                .extracting(DatadisStub.Call::authorization, DatadisStub.Call::status)
                .containsExactly(
                        tuple("Bearer token-a1", 401),
                        tuple("Bearer token-a2", 401));
        assertThat(calls)
                .filteredOn(call -> !(call.cups().equals(refused.getCode()) && call.startDate().equals("2025/10")))
                .hasSize(3)
                .allSatisfy(call -> assertThat(call.status()).isEqualTo(200))
                .extracting(DatadisStub.Call::cups)
                .contains(other.getCode(), refused.getCode());
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.CONSUMPTION_PATH))
                .hasSize(4)
                .allSatisfy(call -> assertThat(call.status()).isEqualTo(200));
    }

    @Test
    @DisplayName("DHI-006 the consumption sync of a community whose configuration is disabled, or that has none, "
            + "answers 409 and makes no Datadis request")
    void theEndpointRefusesCommunitiesWithoutAnEnabledConfiguration() throws Exception {
        UUID disabled = communityWithDatadis(BASE_URL_A, "token-a");
        setDatadisConfigurationRepository.setEnabled(disabled, false);
        UUID withoutConfiguration = createCommunityRepository.create(CommunityMother.random().build()).getId();
        User owner = owner();
        supply(owner, disabled);
        supply(owner, withoutConfiguration);

        for (UUID communityId : List.of(disabled, withoutConfiguration)) {
            String authorization = loginAsCommunityAdmin(communityId);
            datadis.clear();

            mockMvc.perform(post("/api/v1/communities/{communityId}/consumption/datadis/sync", communityId)
                            .header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SyncDatadisConsumptionsBody(2025))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(
                            containsString("POST /api/v1/communities/{communityId}/config/datadis/enable")));

            assertThat(datadis.calls()).isEmpty();
        }
    }

    private UUID communityWithDatadis(String baseUrl, String token, String... laterTokens) {
        UUID communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername(UserMother.randomPersonalId())
                .setPassword("password-" + token)
                .setBaseUrl(baseUrl)
                .setEnabled(true)
                .build());
        communitiesWithDatadis.add(communityId);
        datadis.account(baseUrl, token, laterTokens);
        return communityId;
    }

    private User owner() {
        User owner = UserMother.randomUser();
        owner.enable();
        return createUserRepository.create(owner);
    }

    /**
     * An enabled supply with the distributor code and point type the consumption requests need.
     */
    private Supply supply(User owner, UUID communityId) {
        return createSupplyRepository.create(SupplyMother.random(owner)
                .withEnabled(true)
                .withDistributor(new SupplyDistributor.Builder()
                        .withCode("2")
                        .withName("Distributor")
                        .withPointType(5)
                        .build())
                .build(), UserId.of(owner.getId()), communityId);
    }
}
