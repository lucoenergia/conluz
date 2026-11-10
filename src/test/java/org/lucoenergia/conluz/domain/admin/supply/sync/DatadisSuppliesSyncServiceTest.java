package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisStub;
import org.lucoenergia.conluz.infrastructure.shared.BaseControllerTest;
import org.lucoenergia.conluz.infrastructure.shared.web.rest.ConluzRestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Only the network is replaced, by {@link DatadisStub}. The test extends {@link BaseControllerTest} and replaces
 * exactly {@link ConluzRestClientBuilder}, like the other Datadis stub tests, so that they all share one Spring
 * context.
 */
@Transactional
class DatadisSuppliesSyncServiceTest extends BaseControllerTest {

    private static final String BASE_URL = "http://datadis.test";

    @MockitoBean
    private ConluzRestClientBuilder conluzRestClientBuilder;

    @Autowired
    private DatadisSuppliesSyncService service;
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

    @BeforeEach
    void beforeEach() {
        when(conluzRestClientBuilder.build()).thenAnswer(invocation -> datadis.client());
        when(conluzRestClientBuilder.build(anyBoolean(), any(Duration.class)))
                .thenAnswer(invocation -> datadis.client());
    }

    @Test
    void synchronizeSuppliesSuccessTest() {
        // Assemble
        UUID communityId = communityWithDatadis();
        User user = createUserRepository.create(UserMother.randomUser());

        Supply supplyOne = createSupplyRepository.create(SupplyMother.random().withEnabled(true).build(),
                UserId.of(user.getId()), communityId);
        createSupplyRepository.create(SupplyMother.random().withEnabled(true).build(), UserId.of(user.getId()),
                communityId);

        DatadisSupply datadisSupply = new DatadisSupply.Builder()
                .withCups(supplyOne.getCode())
                .withAddress("TestAddress")
                .withDistributor("EDISTRIBUCION")
                .withDistributorCode("2")
                .withPointType(5)
                .withValidDateFrom("2024/06/01")
                .build();
        datadis.supplies(BASE_URL, user.getPersonalId(), List.of(datadisSupply));

        // Act
        service.synchronizeSupplies(communityId);

        // Assert
        Supply synced = getSupplyRepository.findById(SupplyId.of(supplyOne.getId())).orElseThrow();
        Assertions.assertEquals("2", synced.getDistributor().getCode());
        Assertions.assertEquals(5, synced.getDistributor().getPointType());
    }

    @Test
    void synchronizeUsersWithoutSupply() {
        // Assemble
        UUID communityId = communityWithDatadis();
        User userOne = createUserRepository.create(UserMother.randomUser());
        User userTwo = createUserRepository.create(UserMother.randomUser());

        createSupplyRepository.create(SupplyMother.random().withEnabled(true).build(), UserId.of(userTwo.getId()),
                communityId);
        createSupplyRepository.create(SupplyMother.random().withEnabled(true).build(), UserId.of(userTwo.getId()),
                communityId);

        // Act
        service.synchronizeSupplies(communityId);

        // Assert: only the owner of the community's supplies is requested, once
        assertThat(datadis.calls(BASE_URL, DatadisStub.SUPPLIES_PATH))
                .extracting(DatadisStub.Call::authorizedNif)
                .containsExactly(userTwo.getPersonalId())
                .doesNotContain(userOne.getPersonalId());
    }

    private UUID communityWithDatadis() {
        UUID communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername(UserMother.randomPersonalId())
                .setPassword("password")
                .setBaseUrl(BASE_URL)
                .setEnabled(true)
                .build());
        datadis.account(BASE_URL, "token");
        return communityId;
    }
}
