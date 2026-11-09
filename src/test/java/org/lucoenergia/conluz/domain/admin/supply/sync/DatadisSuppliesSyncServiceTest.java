package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.community.CommunityMother;
import org.lucoenergia.conluz.domain.admin.community.create.CreateCommunityRepository;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.datadis.DatadisConfig;
import org.lucoenergia.conluz.domain.datadis.SetDatadisConfigurationRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.datadis.DatadisAuthorizer;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@Transactional
class DatadisSuppliesSyncServiceTest extends BaseIntegrationTest {

    @MockitoBean
    private GetSupplyRepositoryDatadis getSupplyRepositoryDatadis;
    @MockitoBean
    private DatadisAuthorizer datadisAuthorizer;

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
        when(getSupplyRepositoryDatadis.getSuppliesByUser(any(DatadisConfig.class), anyString(), any(User.class)))
                .thenReturn(List.of(datadisSupply));

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

        when(getSupplyRepositoryDatadis.getSuppliesByUser(any(DatadisConfig.class), anyString(), any(User.class)))
                .thenReturn(List.of());

        // Act
        service.synchronizeSupplies(communityId);

        // Assert
        UUID withoutSupply = userOne.getId();
        UUID withSupplies = userTwo.getId();
        Mockito.verify(getSupplyRepositoryDatadis, never())
                .getSuppliesByUser(any(), any(), argThat(user -> user.getId().equals(withoutSupply)));
        Mockito.verify(getSupplyRepositoryDatadis, times(1))
                .getSuppliesByUser(any(), any(), argThat(user -> user.getId().equals(withSupplies)));
    }

    private UUID communityWithDatadis() {
        UUID communityId = createCommunityRepository.create(CommunityMother.random().build()).getId();
        setDatadisConfigurationRepository.setDatadisConfiguration(communityId, new DatadisConfig.Builder()
                .setUsername(UserMother.randomPersonalId())
                .setPassword("password")
                .setBaseUrl(DatadisConfig.DEFAULT_BASE_URL)
                .setEnabled(true)
                .build());
        when(datadisAuthorizer.getAuthToken(any(DatadisConfig.class))).thenReturn("token");
        return communityId;
    }
}
