package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional
class SetSupplyTariffRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private SetSupplyTariffRepositoryDatabase repository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SupplyEntityMapper supplyEntityMapper;

    @Test
    @DisplayName("Test saving a valid SupplyTariff")
    void save_validSupplyTariff_shouldPersistAndReturnSavedEntity() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);

        SupplyEntity supply = SupplyMother.randomEntity().withUser(user).build();
        supplyRepository.save(supply);

        UUID id = UUID.randomUUID();

        SupplyTariff supplyTariff = new SupplyTariff.Builder()
                .withId(id)
                .withSupply(supplyEntityMapper.map(supply))
                .withValley(10.0)
                .withPeak(20.0)
                .withOffPeak(15.0)
                .build();

        SupplyTariff result = repository.save(supplyTariff);

        assertEquals(supplyTariff.getId(), result.getId());
        assertEquals(supplyTariff.getSupply().getId(), result.getSupply().getId());
        assertEquals(supplyTariff.getValley(), result.getValley());
        assertEquals(supplyTariff.getPeak(), result.getPeak());
        assertEquals(supplyTariff.getOffPeak(), result.getOffPeak());
    }
}