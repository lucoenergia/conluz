package org.lucoenergia.conluz.domain.production.plant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lucoenergia.conluz.domain.admin.supply.partitioncoefficient.GetSupplyPartitionCoefficientRepository;
import org.lucoenergia.conluz.domain.production.huawei.config.DeleteHuaweiConfigRepository;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantRepository;
import org.lucoenergia.conluz.domain.production.plant.delete.DeletePlantService;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasPartitionCoefficientsException;
import org.lucoenergia.conluz.domain.production.plant.delete.PlantHasSharingAgreementsException;
import org.lucoenergia.conluz.domain.production.plant.lock.LockPlantRepository;
import org.lucoenergia.conluz.domain.production.sharingagreement.get.GetSharingAgreementRepository;
import org.lucoenergia.conluz.domain.shared.PlantId;
import org.lucoenergia.conluz.infrastructure.production.plant.delete.DeletePlantServiceImpl;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletePlantServiceTest {

    @Mock
    private DeletePlantRepository deletePlantRepository;
    @Mock
    private LockPlantRepository lockPlantRepository;
    @Mock
    private GetSharingAgreementRepository getSharingAgreementRepository;
    @Mock
    private GetSupplyPartitionCoefficientRepository getSupplyPartitionCoefficientRepository;
    @Mock
    private DeleteHuaweiConfigRepository deleteHuaweiConfigRepository;

    private DeletePlantService service() {
        return new DeletePlantServiceImpl(deletePlantRepository, lockPlantRepository, getSharingAgreementRepository,
                getSupplyPartitionCoefficientRepository, deleteHuaweiConfigRepository);
    }

    @Test
    void plt001_delete_locksThePlantBeforeCheckingAndDeletesItsHuaweiConfigurationBeforeIt() {
        PlantId id = PlantId.of(UUID.randomUUID());
        when(lockPlantRepository.lock(id)).thenReturn(true);

        service().delete(id);

        InOrder order = inOrder(lockPlantRepository, getSharingAgreementRepository,
                getSupplyPartitionCoefficientRepository, deleteHuaweiConfigRepository, deletePlantRepository);
        order.verify(lockPlantRepository).lock(id);
        order.verify(getSharingAgreementRepository).existsByPlant(id);
        order.verify(getSupplyPartitionCoefficientRepository).existsByPlant(id);
        order.verify(deleteHuaweiConfigRepository).deleteByPlant(id);
        order.verify(deletePlantRepository).delete(id);
    }

    @Test
    void plt001_delete_withSharingAgreements_throwsAndDeletesNothing() {
        PlantId id = PlantId.of(UUID.randomUUID());
        when(lockPlantRepository.lock(id)).thenReturn(true);
        when(getSharingAgreementRepository.existsByPlant(id)).thenReturn(true);

        assertThrows(PlantHasSharingAgreementsException.class, () -> service().delete(id));

        verifyNoInteractions(deleteHuaweiConfigRepository, deletePlantRepository);
    }

    @Test
    void plt001_delete_withPartitionCoefficients_throwsAndDeletesNothing() {
        PlantId id = PlantId.of(UUID.randomUUID());
        when(lockPlantRepository.lock(id)).thenReturn(true);
        when(getSupplyPartitionCoefficientRepository.existsByPlant(id)).thenReturn(true);

        assertThrows(PlantHasPartitionCoefficientsException.class, () -> service().delete(id));

        verifyNoInteractions(deleteHuaweiConfigRepository, deletePlantRepository);
    }

    @Test
    void delete_withUnknownPlant_throwsNotFoundAndDeletesNothing() {
        PlantId id = PlantId.of(UUID.randomUUID());
        when(lockPlantRepository.lock(id)).thenReturn(false);

        assertThrows(PlantNotFoundException.class, () -> service().delete(id));

        verifyNoInteractions(getSharingAgreementRepository, getSupplyPartitionCoefficientRepository,
                deleteHuaweiConfigRepository, deletePlantRepository);
    }
}
