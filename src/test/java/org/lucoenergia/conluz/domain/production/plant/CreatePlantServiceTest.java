package org.lucoenergia.conluz.domain.production.plant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantService;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.production.plant.create.CreatePlantServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePlantServiceTest {

    @Mock
    private CreatePlantRepository createPlantRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private LockSupplyRepository lockSupplyRepository;

    private CreatePlantService service() {
        return new CreatePlantServiceImpl(createPlantRepository, getSupplyRepository, lockSupplyRepository);
    }

    @Test
    void sup001_create_locksTheSupplyBeforeWritingThePlant() {
        SupplyId id = SupplyId.of(UUID.randomUUID());
        Plant plant = PlantMother.random().build();
        when(lockSupplyRepository.lockAsReferenced(id)).thenReturn(true);

        service().create(plant, id);

        InOrder order = inOrder(lockSupplyRepository, createPlantRepository);
        order.verify(lockSupplyRepository).lockAsReferenced(id);
        order.verify(createPlantRepository).create(plant, id);
    }

    @Test
    void sup001_create_throwsSupplyNotFound_whenTheSupplyIsDeletedBeforeTheLock() {
        SupplyId id = SupplyId.of(UUID.randomUUID());
        when(lockSupplyRepository.lockAsReferenced(id)).thenReturn(false);

        assertThrows(SupplyNotFoundException.class, () -> service().create(PlantMother.random().build(), id));

        verifyNoInteractions(createPlantRepository);
    }

    @Test
    void sup001_createByCode_locksTheSupplyItFound_andThrowsSupplyNotFound_whenItIsDeletedBeforeTheLock() {
        Supply supply = SupplyMother.random().build();
        SupplyCode code = SupplyCode.of(supply.getCode());
        when(getSupplyRepository.findByCode(code)).thenReturn(Optional.of(supply));
        when(lockSupplyRepository.lockAsReferenced(SupplyId.of(supply.getId()))).thenReturn(false);

        assertThrows(SupplyNotFoundException.class, () -> service().create(PlantMother.random().build(), code));

        verifyNoInteractions(createPlantRepository);
    }
}
