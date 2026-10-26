package org.lucoenergia.conluz.domain.production.plant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.Supply;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.admin.supply.SupplyNotFoundException;
import org.lucoenergia.conluz.domain.admin.supply.get.GetSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantService;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.domain.shared.SupplyCode;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.production.plant.update.UpdatePlantServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdatePlantServiceTest {

    @Mock
    private UpdatePlantRepository updatePlantRepository;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSupplyRepository getSupplyRepository;
    @Mock
    private LockSupplyRepository lockSupplyRepository;

    private UpdatePlantService service() {
        return new UpdatePlantServiceImpl(updatePlantRepository, getPlantRepository, getSupplyRepository,
                lockSupplyRepository);
    }

    @Test
    void sup001_update_movingThePlant_locksTheNewSupplyBeforeWriting() {
        Supply target = SupplyMother.random().build();
        Plant update = movedTo(target);
        when(getSupplyRepository.findByCode(SupplyCode.of(target.getCode()))).thenReturn(Optional.of(target));
        when(lockSupplyRepository.lockAsReferenced(SupplyId.of(target.getId()))).thenReturn(true);

        service().update(update);

        InOrder order = inOrder(lockSupplyRepository, updatePlantRepository);
        order.verify(lockSupplyRepository).lockAsReferenced(SupplyId.of(target.getId()));
        order.verify(updatePlantRepository).update(update);
    }

    @Test
    void sup001_update_movingThePlant_throwsSupplyNotFound_whenTheNewSupplyIsDeletedBeforeTheLock() {
        Supply target = SupplyMother.random().build();
        Plant update = movedTo(target);
        when(getSupplyRepository.findByCode(SupplyCode.of(target.getCode()))).thenReturn(Optional.of(target));
        when(lockSupplyRepository.lockAsReferenced(SupplyId.of(target.getId()))).thenReturn(false);

        assertThrows(SupplyNotFoundException.class, () -> service().update(update));

        verifyNoInteractions(updatePlantRepository);
    }

    @Test
    void update_keepingTheSupply_locksNothing() {
        Plant current = PlantMother.random(SupplyMother.random().build()).build();
        when(getPlantRepository.findById(PlantId.of(current.getId()))).thenReturn(Optional.of(current));

        service().update(current);

        verifyNoInteractions(lockSupplyRepository, getSupplyRepository);
        verify(updatePlantRepository).update(current);
    }

    @Test
    void update_unknownPlant_throwsPlantNotFound_beforeLookingAtTheSupply() {
        Plant update = PlantMother.random(SupplyMother.random().build()).build();
        when(getPlantRepository.findById(PlantId.of(update.getId()))).thenReturn(Optional.empty());

        assertThrows(PlantNotFoundException.class, () -> service().update(update));

        verifyNoInteractions(getSupplyRepository, lockSupplyRepository);
        verify(updatePlantRepository, never()).update(any());
    }

    private Plant movedTo(Supply target) {
        Plant current = PlantMother.random(SupplyMother.random().build()).build();
        when(getPlantRepository.findById(PlantId.of(current.getId()))).thenReturn(Optional.of(current));
        return PlantMother.random(target).withId(current.getId()).build();
    }
}
