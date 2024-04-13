package org.lucoenergia.conluz.domain.admin.supply.sync;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.DatadisSupply;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.create.CreateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepositoryDatadis;
import org.lucoenergia.conluz.domain.admin.supply.update.UpdateSupplyRepository;
import org.lucoenergia.conluz.domain.admin.user.User;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.admin.user.create.CreateUserRepository;
import org.lucoenergia.conluz.domain.admin.user.get.GetUserRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.domain.shared.UserId;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.lucoenergia.conluz.infrastructure.shared.time.DateConverter;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Transactional
class DatadisSuppliesSyncServiceTest extends BaseIntegrationTest {

    @Autowired
    private GetSupplyRepository getSupplyRepository;
    @Autowired
    private UpdateSupplyRepository updateSupplyRepository;
    @Autowired
    private GetUserRepository getUserRepository;
    @Autowired
    private CreateUserRepository createUserRepository;
    @Autowired
    private CreateSupplyRepository createSupplyRepository;
    @Autowired
    private DateConverter dateConverter;
    private final GetSupplyRepositoryDatadis getSupplyRepositoryDatadis = mock(GetSupplyRepositoryDatadis.class);

    private DatadisSuppliesSyncService service;

    @BeforeEach
    void setup() {
        service = new DatadisSuppliesSyncService(getSupplyRepository,
                updateSupplyRepository, getSupplyRepositoryDatadis, getUserRepository, dateConverter);
    }

    @Test
    void synchronizeSuppliesSuccessTest() {
        // Assemble
        String codeOne = "codeOne";
        String codeTwo = "codeTwo";
        String codeThree = "codeThree";

        User user = UserMother.randomUser();
        user = createUserRepository.create(user);

        DatadisSupply datadisSupply = new DatadisSupply.Builder()
                .withCups(codeOne)
                .withAddress("TestAddress")
                .withDistributor("EDISTRIBUCION")
                .withDistributorCode("2")
                .withPointType(5)
                .withValidDateFrom("2024/06/01")
                .build();
        List<DatadisSupply> datadisSupplies = new ArrayList<>();
        datadisSupplies.add(datadisSupply);

        Supply supplyOne = SupplyMother.random()
                .withCode(codeOne)
                .withAddress("OldAddress")
                .build();
        supplyOne = createSupplyRepository.create(supplyOne, UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random().withCode(codeTwo).build(), UserId.of(user.getId()));
        createSupplyRepository.create(SupplyMother.random().withCode(codeThree).build(), UserId.of(user.getId()));

        when(getSupplyRepositoryDatadis.getSuppliesByUser(Mockito.any(User.class))).thenReturn(datadisSupplies);

        // Act
        service.synchronizeSupplies();

        // Assert
        Assertions.assertEquals(datadisSupply.getAddress(), getSupplyRepository.findById(SupplyId.of(supplyOne.getId())).get().getAddress());
    }
}