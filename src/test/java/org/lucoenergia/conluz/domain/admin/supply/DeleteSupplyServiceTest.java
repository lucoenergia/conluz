package org.lucoenergia.conluz.domain.admin.supply;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.delete.DeleteSupplyService;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.admin.supply.delete.SupplyHasPlantsException;
import org.lucoenergia.conluz.domain.admin.supply.lock.LockSupplyRepository;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.plant.get.GetPlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.delete.DeleteSupplyServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteSupplyServiceTest {

    @Mock
    private DeleteSupplyRepository deleteSupplyRepository;
    @Mock
    private LockSupplyRepository lockSupplyRepository;
    @Mock
    private GetPlantRepository getPlantRepository;
    @Mock
    private GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;

    private DeleteSupplyService service() {
        return new DeleteSupplyServiceImpl(deleteSupplyRepository, lockSupplyRepository, getPlantRepository,
                getSupplyPartitionCoefficientRepository);
    }

    @Test
    void sup001_delete_locksTheSupplyBeforeCheckingItsPlantsAndCoefficients() {
        SupplyId id = SupplyId.of(UUID.randomUUID());
        when(lockSupplyRepository.lock(id)).thenReturn(true);

        service().delete(id);

        InOrder order = inOrder(lockSupplyRepository, getPlantRepository, getSupplyPartitionCoefficientRepository,
                deleteSupplyRepository);
        order.verify(lockSupplyRepository).lock(id);
        order.verify(getPlantRepository).existsBySupply(id);
        order.verify(getSupplyPartitionCoefficientRepository).existsBySupply(id);
        order.verify(deleteSupplyRepository).delete(id);
    }

    @Test
    void sup001_delete_withPlants_throwsAndDeletesNothing() {
        SupplyId id = SupplyId.of(UUID.randomUUID());
        when(lockSupplyRepository.lock(id)).thenReturn(true);
        when(getPlantRepository.existsBySupply(id)).thenReturn(true);

        assertThrows(SupplyHasPlantsException.class, () -> service().delete(id));

        verifyNoInteractions(deleteSupplyRepository);
    }

    @Test
    void sup001_delete_withPartitionCoefficients_throwsAndDeletesNothing() {
        SupplyId id = SupplyId.of(UUID.randomUUID());
        when(lockSupplyRepository.lock(id)).thenReturn(true);
        when(getSupplyPartitionCoefficientRepository.existsBySupply(id)).thenReturn(true);

        assertThrows(SupplyHasPartitionCoefficientsException.class, () -> service().delete(id));

        verifyNoInteractions(deleteSupplyRepository);
    }

    @Test
    void delete_withUnknownSupply_throwsNotFoundAndDeletesNothing() {
        SupplyId id = SupplyId.of(UUID.randomUUID());
        when(lockSupplyRepository.lock(id)).thenReturn(false);

        assertThrows(SupplyNotFoundException.class, () -> service().delete(id));

        verifyNoInteractions(getPlantRepository, getSupplyPartitionCoefficientRepository, deleteSupplyRepository);
    }
}
