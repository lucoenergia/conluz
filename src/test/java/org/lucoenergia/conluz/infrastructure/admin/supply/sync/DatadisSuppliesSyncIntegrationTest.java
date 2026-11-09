package org.lucoenergia.conluz.infrastructure.admin.supply.sync;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.distributor.SupplyDistributor;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.sync.DatadisSuppliesSyncService;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.datadis.sync.DatadisSyncService;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisStub;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

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

    @MockitoBean
    private ConluzRestClientBuilder conluzRestClientBuilder;

    @Autowired
    private DatadisSuppliesSyncService datadisSuppliesSyncService;
    @Autowired
    private DatadisSyncService datadisSyncService;
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
        when(conluzRestClientBuilder.build()).thenAnswer(invocation -> datadis.client());
        when(conluzRestClientBuilder.build(anyBoolean(), any(Duration.class)))
                .thenAnswer(invocation -> datadis.client());
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

        datadisSuppliesSyncService.synchronizeSupplies(communityA);
        datadisSuppliesSyncService.synchronizeSupplies(communityB);

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
        return createSupplyRepository.create(SupplyMother.random(owner)
                .withEnabled(true)
                .withDistributor(null)
                .withContract(null)
                .build(), UserId.of(owner.getId()), communityId);
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
