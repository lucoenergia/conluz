package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.contract.SupplyContract;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.admin.user.auth.throttle.LogCapture;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisStub;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Datadis supplies sync end to end, against two communities whose Datadis accounts live at different base URLs
 * with different credentials. Only the network is replaced, by {@link DatadisStub}. The test is not transactional:
 * the sync must run without a transaction around its Datadis calls, and commit each supply on its own.
 */
class DatadisSuppliesSyncIntegrationTest extends BaseControllerTest {

    private static final String BASE_URL_A = "http://datadis-a.test";
    private static final String BASE_URL_B = "http://datadis-b.test";
    private static final String TOKEN_A = "token-a";
    private static final String TOKEN_B = "token-b";

    @Autowired
    private DatadisSuppliesSyncService datadisSuppliesSyncService;
    @Autowired
    private DatadisSuppliesSyncDailyJob datadisSuppliesSyncDailyJob;
    @Autowired
    private GetSupplyRepository getSupplyRepository;
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
    @DisplayName("DSS-001 each community's supplies are requested with its own token and base URL, after one login "
            + "per community")
    void eachCommunityIsSyncedWithItsOwnConfiguration() {
        UUID communityA = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        UUID communityB = communityWithDatadis(BASE_URL_B, UserMother.randomPersonalId(), TOKEN_B, true);
        User ownerA = owner();
        User ownerB = owner();
        User otherOwnerB = owner();
        Supply a1 = supply(ownerA, communityA);
        Supply a2 = supply(ownerA, communityA);
        Supply b1 = supply(ownerB, communityB);
        Supply b2 = supply(otherOwnerB, communityB);
        datadis.supplies(BASE_URL_A, ownerA.getPersonalId(), List.of(entry(a1.getCode(), "1"), entry(a2.getCode(), "2")));
        datadis.supplies(BASE_URL_B, ownerB.getPersonalId(), List.of(entry(b1.getCode(), "3")));
        datadis.supplies(BASE_URL_B, otherOwnerB.getPersonalId(), List.of(entry(b2.getCode(), "4")));

        datadisSuppliesSyncDailyJob.run();

        assertThat(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH)).hasSize(1);
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.LOGIN_PATH)).hasSize(1);
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.SUPPLIES_PATH))
                .hasSize(1)
                .allSatisfy(call -> assertThat(call.authorization()).isEqualTo("Bearer " + TOKEN_A));
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.SUPPLIES_PATH))
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.authorization()).isEqualTo("Bearer " + TOKEN_B));
        assertThat(distributorCode(a1)).isEqualTo("1");
        assertThat(distributorCode(a2)).isEqualTo("2");
        assertThat(distributorCode(b1)).isEqualTo("3");
        assertThat(distributorCode(b2)).isEqualTo("4");
    }

    @Test
    @DisplayName("DSS-004 authorizedNif is omitted for the community whose Datadis account the owner holds, and sent "
            + "for the other, in the supplies and the consumption requests")
    void authorizedNifIsDecidedAgainstTheSupplysCommunityConfiguration() {
        User owner = owner();
        UUID communityA = communityWithDatadis(BASE_URL_A, owner.getPersonalId(), TOKEN_A, true);
        UUID communityB = communityWithDatadis(BASE_URL_B, UserMother.randomPersonalId(), TOKEN_B, true);
        Supply a1 = supply(owner, communityA);
        Supply a2 = supply(owner, communityA);
        Supply b1 = supply(owner, communityB);
        Supply b2 = supply(owner, communityB);
        datadis.supplies(BASE_URL_A, null, List.of(entry(a1.getCode(), "1"), entry(a2.getCode(), "1")));
        datadis.supplies(BASE_URL_B, owner.getPersonalId(),
                List.of(entry(b1.getCode(), "2"), entry(b2.getCode(), "2")));

        datadisSuppliesSyncService.synchronizeSupplies(communityA);
        datadisSuppliesSyncService.synchronizeSupplies(communityB);
        LocalDate month = LocalDate.of(2025, 11, 1);
        datadisSyncService.synchronize(communityA, month, month);
        datadisSyncService.synchronize(communityB, month, month);

        assertThat(datadis.calls(BASE_URL_A, DatadisStub.SUPPLIES_PATH))
                .singleElement()
                .satisfies(call -> assertThat(call.authorizedNif()).isNull());
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.CONSUMPTION_PATH))
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.authorizedNif()).isNull());
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.SUPPLIES_PATH))
                .singleElement()
                .satisfies(call -> assertThat(call.authorizedNif()).isEqualTo(owner.getPersonalId()));
        assertThat(datadis.calls(BASE_URL_B, DatadisStub.CONSUMPTION_PATH))
                .hasSize(2)
                .allSatisfy(call -> assertThat(call.authorizedNif()).isEqualTo(owner.getPersonalId()));
        assertThat(distributorCode(a1)).isEqualTo("1");
        assertThat(distributorCode(b2)).isEqualTo("2");
    }

    @Test
    @DisplayName("DSS-002 a community whose Datadis login fails is logged at ERROR, and the other community is still "
            + "synced")
    void aFailingCommunityDoesNotStopTheOthers() {
        UUID communityA = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        UUID communityB = communityWithDatadis(BASE_URL_B, UserMother.randomPersonalId(), TOKEN_B, true);
        User ownerA = owner();
        User ownerB = owner();
        Supply a1 = supply(ownerA, communityA);
        Supply b1 = supply(ownerB, communityB);
        Supply b2 = supply(ownerB, communityB);
        datadis.failLogin(BASE_URL_A, 500);
        datadis.supplies(BASE_URL_A, ownerA.getPersonalId(), List.of(entry(a1.getCode(), "1")));
        datadis.supplies(BASE_URL_B, ownerB.getPersonalId(), List.of(entry(b1.getCode(), "2"), entry(b2.getCode(), "2")));

        try (LogCapture logs = LogCapture.of(DatadisSuppliesSyncDailyJob.class)) {
            datadisSuppliesSyncDailyJob.run();

            assertThat(logs.warningsAndAbove())
                    .filteredOn(event -> communityA.equals(argument(event, 0)))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                        assertThat(event.getMessage()).isEqualTo("Datadis supplies sync failed for community {}");
                        assertThat(event.getThrowableProxy()).isNotNull();
                    });
            assertThat(logs.warningsAndAbove())
                    .noneSatisfy(event -> assertThat(argument(event, 0)).isEqualTo(communityB));
        }
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.SUPPLIES_PATH)).isEmpty();
        assertThat(distributorCode(a1)).isNull();
        assertThat(distributorCode(b1)).isEqualTo("2");
        assertThat(distributorCode(b2)).isEqualTo("2");
    }

    @Test
    @DisplayName("DSS-003 the job makes no Datadis request for a community whose configuration is disabled, or that "
            + "has none")
    void theJobSkipsCommunitiesWithoutAnEnabledConfiguration() {
        UUID disabled = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, false);
        UUID withoutConfiguration = createCommunityRepository.create(CommunityMother.random().build()).getId();
        User ownerOfDisabled = owner();
        User ownerWithoutConfiguration = owner();
        Supply d1 = supply(ownerOfDisabled, disabled);
        Supply d2 = supply(ownerOfDisabled, disabled);
        Supply n1 = supply(ownerWithoutConfiguration, withoutConfiguration);
        Supply n2 = supply(ownerWithoutConfiguration, withoutConfiguration);
        datadis.supplies(BASE_URL_A, ownerOfDisabled.getPersonalId(),
                List.of(entry(d1.getCode(), "1"), entry(d2.getCode(), "1")));

        datadisSuppliesSyncDailyJob.run();

        assertThat(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH)).isEmpty();
        assertThat(datadis.calls(BASE_URL_A, DatadisStub.SUPPLIES_PATH)).isEmpty();
        assertThat(datadis.calls())
                .noneSatisfy(call -> assertThat(call.authorizedNif())
                        .isIn(ownerOfDisabled.getPersonalId(), ownerWithoutConfiguration.getPersonalId()));
        assertThat(List.of(d1, d2, n1, n2)).allSatisfy(supply -> assertThat(distributorCode(supply)).isNull());
    }

    @Test
    @DisplayName("DSS-003 the per-community sync of a community whose configuration is disabled, or that has none, "
            + "answers 409 as the consumption sync does, and makes no Datadis request")
    void theEndpointRefusesCommunitiesWithoutAnEnabledConfiguration() throws Exception {
        UUID disabled = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, false);
        UUID withoutConfiguration = createCommunityRepository.create(CommunityMother.random().build()).getId();
        User owner = owner();
        supply(owner, disabled);
        supply(owner, withoutConfiguration);

        for (UUID communityId : List.of(disabled, withoutConfiguration)) {
            String authorization = loginAsCommunityAdmin(communityId);
            datadis.clear();

            mockMvc.perform(post("/api/v1/communities/{communityId}/supplies/datadis/sync", communityId)
                            .header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(
                            containsString("POST /api/v1/communities/{communityId}/config/datadis/enable")));

            assertThat(datadis.calls()).isEmpty();
        }
    }

    @Test
    @DisplayName("DSS-005 a supply stored in the 20-character form, or in lower case, is matched by the 22-character "
            + "upper-case CUPS Datadis returns")
    void suppliesAreMatchedAcrossCupsFormsAndCase() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        String shortForm = SupplyMother.randomCups();
        String lowerCase = SupplyMother.randomCups();
        // Stored through the repository, which keeps a code as given: codes like these were written before #532
        Supply stored20 = supply(owner, communityId, shortForm.substring(0, 20), true);
        Supply storedLower = supply(owner, communityId, lowerCase.toLowerCase(Locale.ROOT), true);
        datadis.supplies(BASE_URL_A, owner.getPersonalId(), List.of(entry(shortForm, "1"), entry(lowerCase, "2")));

        datadisSuppliesSyncService.synchronizeSupplies(communityId);

        assertThat(distributorCode(stored20)).isEqualTo("1");
        assertThat(distributorCode(storedLower)).isEqualTo("2");
        assertThat(getSupplyRepository.findById(SupplyId.of(stored20.getId())).orElseThrow().getContract()
                .getValidDateFrom()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    @DisplayName("DSS-006 a registered supply Datadis does not return is logged at WARN with its id and left "
            + "unchanged; a Datadis supply that is not registered is neither logged nor written")
    void aRegisteredSupplyDatadisDoesNotReturnIsReported() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        Supply s1 = supply(owner, communityId);
        Supply s2 = supply(owner, communityId);
        Supply missing = supply(owner, communityId);
        String unregistered = SupplyMother.randomCups();
        datadis.supplies(BASE_URL_A, owner.getPersonalId(),
                List.of(entry(s1.getCode(), "1"), entry(unregistered, "9"), entry(s2.getCode(), "1")));
        Supply missingBefore = stored(missing);

        try (LogCapture logs = LogCapture.of(DatadisSuppliesSyncServiceImpl.class)) {
            datadisSuppliesSyncService.synchronizeSupplies(communityId);

            assertThat(logs.warningsAndAbove()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getMessage()).isEqualTo("Registered supply {} not returned by Datadis in community {}");
                assertThat(event.getArgumentArray()).containsExactly(missing.getId(), communityId);
            });
            assertThat(logs.all()).noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(unregistered));
        }
        assertThat(distributorCode(s1)).isEqualTo("1");
        assertThat(distributorCode(s2)).isEqualTo("1");
        assertThat(getSupplyRepository.findById(SupplyId.of(missing.getId())).orElseThrow())
                .usingRecursiveComparison().isEqualTo(missingBefore);
        assertThat(getSupplyRepository.findAllByCommunityId(communityId))
                .extracting(Supply::getCode)
                .containsExactlyInAnyOrder(s1.getCode(), s2.getCode(), missing.getCode());
    }

    @Test
    @DisplayName("DSS-006 when Datadis refuses an owner's supplies request, each registered supply of that owner is "
            + "logged at WARN")
    void anOwnerWhoseRequestFailsHasEachSupplyReported() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User answered = owner();
        User refused = owner();
        Supply a1 = supply(answered, communityId);
        Supply r1 = supply(refused, communityId);
        Supply r2 = supply(refused, communityId);
        datadis.supplies(BASE_URL_A, answered.getPersonalId(), List.of(entry(a1.getCode(), "1")));
        datadis.failSupplies(BASE_URL_A, refused.getPersonalId(), 500);

        try (LogCapture logs = LogCapture.of(DatadisSuppliesSyncServiceImpl.class)) {
            datadisSuppliesSyncService.synchronizeSupplies(communityId);

            assertThat(logs.warningsAndAbove())
                    .allSatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getMessage())
                                .isEqualTo("Registered supply {} not returned by Datadis in community {}");
                    })
                    .extracting(event -> event.getArgumentArray()[0])
                    .containsExactlyInAnyOrder(r1.getId(), r2.getId());
        }
        assertThat(distributorCode(a1)).isEqualTo("1");
        assertThat(distributorCode(r1)).isNull();
        assertThat(distributorCode(r2)).isNull();
    }

    @Test
    @DisplayName("DSS-006 a disabled supply is neither reported when Datadis does not return it nor updated when it "
            + "does")
    void disabledSuppliesAreNotSynced() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        Supply enabled = supply(owner, communityId);
        Supply disabledReturned = supply(owner, communityId, SupplyMother.randomCups(), false);
        Supply disabledMissing = supply(owner, communityId, SupplyMother.randomCups(), false);
        datadis.supplies(BASE_URL_A, owner.getPersonalId(),
                List.of(entry(enabled.getCode(), "1"), entry(disabledReturned.getCode(), "1")));

        try (LogCapture logs = LogCapture.of(DatadisSuppliesSyncServiceImpl.class)) {
            datadisSuppliesSyncService.synchronizeSupplies(communityId);

            assertThat(logs.warningsAndAbove()).isEmpty();
        }
        assertThat(distributorCode(enabled)).isEqualTo("1");
        assertThat(distributorCode(disabledReturned)).isNull();
        assertThat(distributorCode(disabledMissing)).isNull();
    }

    @Test
    @DisplayName("DSS-007 a Datadis supply that matches two registered supplies, by the 20- and 22-character forms "
            + "of its CUPS, is skipped with an ERROR naming both, and the rest of the community is synced")
    @SuppressWarnings("unchecked")
    void anAmbiguousMatchIsSkipped() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        String cups = SupplyMother.randomCups();
        Supply longForm = supply(owner, communityId, cups, true);
        Supply shortForm = supply(owner, communityId, cups.substring(0, 20), true);
        Supply other = supply(owner, communityId);
        datadis.supplies(BASE_URL_A, owner.getPersonalId(), List.of(entry(cups, "1"), entry(other.getCode(), "2")));

        try (LogCapture logs = LogCapture.of(DatadisSuppliesSyncServiceImpl.class)) {
            datadisSuppliesSyncService.synchronizeSupplies(communityId);

            assertThat(logs.warningsAndAbove()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage())
                        .isEqualTo("Datadis supply matches several registered supplies in community {}: {}");
                assertThat(event.getArgumentArray()[0]).isEqualTo(communityId);
                assertThat((List<Object>) event.getArgumentArray()[1])
                        .containsExactlyInAnyOrder(longForm.getId(), shortForm.getId());
                assertThat(event.getFormattedMessage()).doesNotContain(cups.substring(0, 20));
            });
        }
        assertThat(distributorCode(longForm)).isNull();
        assertThat(distributorCode(shortForm)).isNull();
        assertThat(distributorCode(other)).isEqualTo("2");
    }

    @Test
    @DisplayName("DSS-008 no database transaction is active while Datadis is called, for the login or any supplies "
            + "request")
    void datadisIsCalledOutsideAnyTransaction() {
        UUID communityA = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        UUID communityB = communityWithDatadis(BASE_URL_B, UserMother.randomPersonalId(), TOKEN_B, true);
        User ownerA = owner();
        User otherOwnerA = owner();
        User ownerB = owner();
        Supply a1 = supply(ownerA, communityA);
        Supply a2 = supply(otherOwnerA, communityA);
        Supply b1 = supply(ownerB, communityB);
        Supply b2 = supply(ownerB, communityB);
        datadis.supplies(BASE_URL_A, ownerA.getPersonalId(), List.of(entry(a1.getCode(), "1")));
        datadis.supplies(BASE_URL_A, otherOwnerA.getPersonalId(), List.of(entry(a2.getCode(), "1")));
        datadis.supplies(BASE_URL_B, ownerB.getPersonalId(), List.of(entry(b1.getCode(), "2"), entry(b2.getCode(), "2")));

        datadisSuppliesSyncDailyJob.run();

        List<DatadisStub.Call> calls = new ArrayList<>(datadis.calls(BASE_URL_A, DatadisStub.LOGIN_PATH));
        calls.addAll(datadis.calls(BASE_URL_A, DatadisStub.SUPPLIES_PATH));
        calls.addAll(datadis.calls(BASE_URL_B, DatadisStub.LOGIN_PATH));
        calls.addAll(datadis.calls(BASE_URL_B, DatadisStub.SUPPLIES_PATH));
        assertThat(calls).hasSize(5).noneSatisfy(call -> assertThat(call.transactionActive()).isTrue());
        assertThat(List.of(a1, a2)).allSatisfy(supply -> assertThat(distributorCode(supply)).isEqualTo("1"));
        assertThat(List.of(b1, b2)).allSatisfy(supply -> assertThat(distributorCode(supply)).isEqualTo("2"));
    }

    @Test
    @DisplayName("DSS-008 when one of three supplies cannot be written, the other two are kept, and the failure is "
            + "logged at ERROR with the community and the supply")
    void eachSupplyIsWrittenOnItsOwn() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        Supply first = supply(owner, communityId);
        Supply second = supply(owner, communityId);
        Supply third = supply(owner, communityId);
        // A distributor name longer than its column holds: the database refuses that supply's write
        DatadisSupply unwritable = new DatadisSupply.Builder()
                .withCups(second.getCode())
                .withAddress("Datadis address")
                .withDistributor("D".repeat(251))
                .withDistributorCode("1")
                .withPointType(5)
                .withValidDateFrom("2024/06/01")
                .build();
        datadis.supplies(BASE_URL_A, owner.getPersonalId(),
                List.of(entry(first.getCode(), "1"), unwritable, entry(third.getCode(), "1")));

        try (LogCapture logs = LogCapture.of(DatadisSuppliesSyncServiceImpl.class)) {
            datadisSuppliesSyncService.synchronizeSupplies(communityId);

            assertThat(logs.warningsAndAbove()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage()).isEqualTo("Failed to update supply from Datadis in community {}: {}");
                assertThat(event.getArgumentArray()).startsWith(communityId, second.getId());
                assertThat(event.getThrowableProxy()).isNotNull();
            });
        }
        assertThat(distributorCode(first)).isEqualTo("1");
        assertThat(distributorCode(second)).isNull();
        assertThat(distributorCode(third)).isEqualTo("1");
    }

    @Test
    @DisplayName("DSS-009 a supply gets the distributor, point type and start date of its latest Datadis contract, "
            + "whatever the order Datadis returns its contracts in")
    void theLatestContractIsStoredWhateverTheOrder() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        Supply forward = supply(owner, communityId);
        Supply backward = supply(owner, communityId);
        List<DatadisSupply> forwardContracts = contracts(forward.getCode());
        List<DatadisSupply> backwardContracts = new ArrayList<>(contracts(backward.getCode()));
        Collections.reverse(backwardContracts);
        List<DatadisSupply> entries = new ArrayList<>(forwardContracts);
        entries.addAll(backwardContracts);
        datadis.supplies(BASE_URL_A, owner.getPersonalId(), entries);

        datadisSuppliesSyncService.synchronizeSupplies(communityId);

        assertThat(List.of(stored(forward), stored(backward))).allSatisfy(supply -> {
            assertThat(supply.getDistributor().getCode()).isEqualTo("2");
            assertThat(supply.getDistributor().getName()).isEqualTo("Distributor 2");
            assertThat(supply.getDistributor().getPointType()).isEqualTo(3);
            assertThat(supply.getContract().getValidDateFrom()).isEqualTo(LocalDate.of(2024, 6, 1));
        });

        // A second run, with the orders swapped, stores the same
        datadis.supplies(BASE_URL_A, owner.getPersonalId(), List.of(backwardContracts.get(0), backwardContracts.get(1),
                backwardContracts.get(2), forwardContracts.get(2), forwardContracts.get(1), forwardContracts.get(0)));
        datadisSuppliesSyncService.synchronizeSupplies(communityId);

        assertThat(List.of(stored(forward), stored(backward))).allSatisfy(supply -> {
            assertThat(supply.getDistributor().getCode()).isEqualTo("2");
            assertThat(supply.getContract().getValidDateFrom()).isEqualTo(LocalDate.of(2024, 6, 1));
        });
    }

    @Test
    @DisplayName("DSS-009 a Datadis contract whose start date cannot be read keeps the stored start date, and still "
            + "updates the distributor and point type")
    void anUnreadableStartDateKeepsTheStoredOne() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        Supply supply = createSupplyRepository.create(SupplyMother.random(owner)
                .withCode(SupplyMother.randomCups())
                .withEnabled(true)
                .withDistributor(null)
                .withContract(new SupplyContract.Builder().withValidDateFrom(LocalDate.of(2024, 6, 1)).build())
                .build(), UserId.of(owner.getId()), communityId);
        datadis.supplies(BASE_URL_A, owner.getPersonalId(),
                List.of(contract(supply.getCode(), "not a date", "7", 2)));

        datadisSuppliesSyncService.synchronizeSupplies(communityId);

        Supply stored = stored(supply);
        assertThat(stored.getContract().getValidDateFrom()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(stored.getDistributor().getCode()).isEqualTo("7");
        assertThat(stored.getDistributor().getPointType()).isEqualTo(2);
    }

    @Test
    @DisplayName("DSS-010 Datadis' address fills a blank address, and never replaces one the community admin entered")
    void anEnteredAddressIsKept() {
        UUID communityId = communityWithDatadis(BASE_URL_A, UserMother.randomPersonalId(), TOKEN_A, true);
        User owner = owner();
        // Built with a blank address, which a creation refuses: rows stored before that validation may have one
        Supply blank = createSupplyRepository.create(SupplyMother.random(owner)
                .withCode(SupplyMother.randomCups())
                .withEnabled(true)
                .withAddress(" ")
                .withDistributor(null)
                .withContract(null)
                .build(), UserId.of(owner.getId()), communityId);
        Supply entered = supply(owner, communityId);
        datadis.supplies(BASE_URL_A, owner.getPersonalId(),
                List.of(entry(blank.getCode(), "1"), entry(entered.getCode(), "1")));

        datadisSuppliesSyncService.synchronizeSupplies(communityId);

        assertThat(stored(blank).getAddress()).isEqualTo("Datadis address");
        assertThat(stored(entered).getAddress()).isEqualTo(entered.getAddress()).isNotEqualTo("Datadis address");
        assertThat(distributorCode(entered)).isEqualTo("1");
    }

    private static Object argument(ILoggingEvent event, int index) {
        Object[] arguments = event.getArgumentArray();
        return arguments == null || arguments.length <= index ? null : arguments[index];
    }

    private UUID communityWithDatadis(String baseUrl, String username, String token, boolean enabled) {
        UUID communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername(username)
                .setPassword("password-" + token)
                .setBaseUrl(baseUrl)
                .setEnabled(enabled)
                .build());
        communitiesWithDatadis.add(communityId);
        datadis.account(baseUrl, token);
        return communityId;
    }

    private User owner() {
        User owner = UserMother.randomUser();
        owner.enable();
        return createUserRepository.create(owner);
    }

    /**
     * An enabled supply with no distributor yet, as a supply is before its first sync.
     */
    private Supply supply(User owner, UUID communityId) {
        return supply(owner, communityId, SupplyMother.randomCups(), true);
    }

    private Supply supply(User owner, UUID communityId, String code, boolean enabled) {
        return createSupplyRepository.create(SupplyMother.random(owner)
                .withCode(code)
                .withEnabled(enabled)
                .withDistributor(null)
                .withContract(null)
                .build(), UserId.of(owner.getId()), communityId);
    }

    private Supply stored(Supply supply) {
        return getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow();
    }

    /**
     * Three contracts of one CUPS: an older one, the latest, and one without a start date.
     */
    private static List<DatadisSupply> contracts(String cups) {
        return List.of(
                contract(cups, "2023/01/01", "1", 5),
                contract(cups, "2024/06/01", "2", 3),
                contract(cups, null, "3", 4));
    }

    private static DatadisSupply contract(String cups, String validDateFrom, String distributorCode, int pointType) {
        return new DatadisSupply.Builder()
                .withCups(cups)
                .withAddress("Datadis address")
                .withDistributor("Distributor " + distributorCode)
                .withDistributorCode(distributorCode)
                .withPointType(pointType)
                .withValidDateFrom(validDateFrom)
                .build();
    }

    private static DatadisSupply entry(String cups, String distributorCode) {
        return new DatadisSupply.Builder()
                .withCups(cups)
                .withAddress("Datadis address")
                .withDistributor("Distributor " + distributorCode)
                .withDistributorCode(distributorCode)
                .withPointType(5)
                .withValidDateFrom("2024/06/01")
                .build();
    }

    private SupplyDistributor distributor(Supply supply) {
        return getSupplyRepository.findById(SupplyId.of(supply.getId())).orElseThrow().getDistributor();
    }

    private String distributorCode(Supply supply) {
        SupplyDistributor distributor = distributor(supply);
        return distributor == null ? null : distributor.getCode();
    }
}
