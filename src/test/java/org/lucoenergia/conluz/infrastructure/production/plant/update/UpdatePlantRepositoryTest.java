package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write check of PLT-002 on its own, with the persistence mocked: the database constraint behind it
 * answers the same refusal, so only a test without the database can tell the check is there.
 */
class UpdatePlantRepositoryTest {

    private final PlantRepository plantRepository = mock(PlantRepository.class);
    private final SupplyRepository supplyRepository = mock(SupplyRepository.class);
    private final UpdatePlantRepository repository = new UpdatePlantRepositoryDatabase(plantRepository,
            supplyRepository, mock(PlantEntityMapper.class));

    @Test
    @DisplayName("PLT-002 moving a plant to a supply that already has a plant is refused before anything is saved or changed")
    void movingAPlantToASupplyThatAlreadyHasAPlantIsRefusedBeforeAnythingIsSaved() {
        SupplyEntity currentSupply = SupplyMother.randomEntity().withCode("ES0000000000000000001A").build();
        SupplyEntity targetSupply = SupplyMother.randomEntity().withCode("ES0000000000000000002B").build();
        PlantEntity current = PlantMother.randomPlantEntity().withSupply(currentSupply).build();
        String originalName = current.getName();
        when(plantRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(supplyRepository.findByCode(targetSupply.getCode())).thenReturn(Optional.of(targetSupply));
        when(plantRepository.existsBySupplyId(targetSupply.getId())).thenReturn(true);
        Plant update = PlantMother.random(SupplyMother.random().withCode(targetSupply.getCode()).build())
                .withId(current.getId())
                .withName("Renamed")
                .build();

        SupplyAlreadyHasPlantException exception = assertThrows(SupplyAlreadyHasPlantException.class,
                () -> repository.update(update));

        assertEquals(SupplyId.of(targetSupply.getId()), exception.getSupplyId());
        verify(plantRepository, never()).save(any());
        verify(plantRepository, never()).saveAndFlush(any());
        assertSame(currentSupply, current.getSupply());
        assertEquals(originalName, current.getName());
    }
}
