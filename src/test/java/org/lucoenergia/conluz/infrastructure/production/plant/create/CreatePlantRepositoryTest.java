package org.lucoenergia.conluz.infrastructure.production.plant.create;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.production.plant.create.CreatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntityMapper;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write check of PLT-002 on its own, with the persistence mocked: the database constraint behind it
 * answers the same refusal, so only a test without the database can tell the check is there.
 */
class CreatePlantRepositoryTest {

    private final SupplyRepository supplyRepository = mock(SupplyRepository.class);
    private final PlantRepository plantRepository = mock(PlantRepository.class);
    private final CreatePlantRepository repository = new CreatePlantRepositoryDatabase(supplyRepository,
            plantRepository, mock(SupplyEntityMapper.class), mock(PlantEntityMapper.class));

    @Test
    @DisplayName("PLT-002 creating a plant on a supply that already has a plant is refused before anything is saved")
    void creatingAPlantOnASupplyThatAlreadyHasAPlantIsRefusedBeforeAnythingIsSaved() {
        SupplyEntity supply = SupplyMother.randomEntity().build();
        SupplyId supplyId = SupplyId.of(supply.getId());
        when(supplyRepository.findById(supply.getId())).thenReturn(Optional.of(supply));
        when(plantRepository.countByProviderCode(anyString())).thenReturn(0);
        when(plantRepository.existsBySupplyId(supply.getId())).thenReturn(true);

        SupplyAlreadyHasPlantException exception = assertThrows(SupplyAlreadyHasPlantException.class,
                () -> repository.create(PlantMother.random().build(), supplyId));

        assertEquals(supplyId, exception.getSupplyId());
        verify(plantRepository, never()).save(any());
        verify(plantRepository, never()).saveAndFlush(any());
    }
}
