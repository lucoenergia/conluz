package org.lucoenergia.conluz.infrastructure.admin.supply.tariff.get;

import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyTariff;
import org.lucoenergia.conluz.domain.admin.user.UserMother;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.tariff.SupplyTariffRepository;
import org.lucoenergia.conluz.infrastructure.admin.user.UserEntity;
import org.lucoenergia.conluz.infrastructure.admin.user.UserRepository;
import org.lucoenergia.conluz.infrastructure.shared.BaseIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class GetSupplyTariffRepositoryDatabaseTest extends BaseIntegrationTest {

    @Autowired
    private SupplyTariffRepository jpaRepository;
    @Autowired
    private SupplyRepository supplyRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GetSupplyTariffRepositoryDatabase repository;

    @Test
    void testFindBySupplyId_WhenTariffExists() {
        UserEntity user = UserMother.randomUserEntity();
        userRepository.save(user);

        SupplyEntity supply = SupplyMother.randomEntity().withUser(user).build();
        supplyRepository.save(supply);

        SupplyTariffEntity entity = new SupplyTariffEntity();
        entity.setId(UUID.randomUUID());
        entity.setSupply(supply);
        entity.setValley(10.0);
        entity.setPeak(20.0);
        entity.setOffPeak(15.0);
        jpaRepository.save(entity);

        Optional<SupplyTariff> result = repository.findBySupplyId(SupplyId.of(supply.getId()));

        assertTrue(result.isPresent());
        assertEquals(entity.getId(), result.get().getId());
        assertEquals(entity.getValley(), result.get().getValley());
        assertEquals(entity.getPeak(), result.get().getPeak());
        assertEquals(entity.getOffPeak(), result.get().getOffPeak());
    }

    @Test
    void testFindBySupplyId_WhenTariffDoesNotExist() {
        UUID supplyIdUUID = UUID.randomUUID();
        SupplyId supplyId = SupplyId.of(supplyIdUUID);

        Optional<SupplyTariff> result = repository.findBySupplyId(supplyId);

        assertFalse(result.isPresent());
    }
}