package org.lucoenergia.conluz.infrastructure.production.plant.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lucoenergia.conluz.domain.admin.supply.SupplyMother;
import org.lucoenergia.conluz.domain.production.plant.Plant;
import org.lucoenergia.conluz.domain.production.plant.PlantCilAlreadyInUseException;
import org.lucoenergia.conluz.domain.production.plant.PlantMother;
import org.lucoenergia.conluz.domain.production.plant.SupplyAlreadyHasPlantException;
import org.lucoenergia.conluz.domain.production.plant.update.UpdatePlantRepository;
import org.lucoenergia.conluz.domain.shared.SupplyId;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyEntity;
import org.lucoenergia.conluz.infrastructure.admin.supply.SupplyRepository;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntity;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantEntityMapper;
import org.lucoenergia.conluz.infrastructure.production.plant.PlantRepository;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

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
 * The write checks of PLT-002 and PLT-005 on their own, with the persistence mocked: the database constraints
 * behind them answer the same refusals, so only a test without the database can tell the checks are there.
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

    @Test
    @DisplayName("PLT-005 updating a plant with a CIL another plant holds is refused before anything is saved or changed")
    void updatingAPlantWithACilAnotherPlantHoldsIsRefusedBeforeAnythingIsSaved() {
        PlantEntity current = PlantMother.randomPlantEntity().withCil(null).build();
        String originalName = current.getName();
        when(plantRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(plantRepository.existsByCilAndIdNot(CIL, current.getId())).thenReturn(true);

        assertThrows(PlantCilAlreadyInUseException.class, () -> repository.update(updateOf(current)));

        verify(plantRepository, never()).save(any());
        verify(plantRepository, never()).saveAndFlush(any());
        assertEquals(originalName, current.getName());
        assertEquals(null, current.getCil());
    }

    @Test
    @DisplayName("PLT-005 AC8 a plant's own CIL is left out of the check, so keeping it is not a conflict")
    void aPlantsOwnCilIsLeftOutOfTheCheck() {
        PlantEntity current = PlantMother.randomPlantEntity().withCil(CIL).build();
        when(plantRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(plantRepository.saveAndFlush(current)).thenReturn(current);

        repository.update(updateOf(current));

        verify(plantRepository).existsByCilAndIdNot(CIL, current.getId());
        assertEquals("Renamed", current.getName());
    }

    @Test
    @DisplayName("PLT-005 AC9 a concurrent writer rejected by plants_cil_uq is refused as PLANT_CIL_ALREADY_IN_USE")
    void aConcurrentWriterRejectedByTheCilConstraintIsRefusedAsACilConflict() {
        PlantEntity current = PlantMother.randomPlantEntity().withCil(null).build();
        when(plantRepository.findById(current.getId())).thenReturn(Optional.of(current));
        DataIntegrityViolationException violation = violationOf("plants_cil_uq");
        when(plantRepository.saveAndFlush(current)).thenThrow(violation);

        PlantCilAlreadyInUseException exception = assertThrows(PlantCilAlreadyInUseException.class,
                () -> repository.update(updateOf(current)));

        assertSame(violation, exception.getCause());
    }

    private static final String CIL = "ES0031406912345678AB1F001";

    /** An update of the given plant that keeps its supply, renames it and sets {@link #CIL}. */
    private static Plant updateOf(PlantEntity current) {
        return PlantMother.random(SupplyMother.random().withCode(current.getSupply().getCode()).build())
                .withId(current.getId())
                .withName("Renamed")
                .withCil(CIL)
                .build();
    }

    private static DataIntegrityViolationException violationOf(String constraintName) {
        ServerErrorMessage serverError = new ServerErrorMessage("SERROR\0C23505\0n" + constraintName + "\0");
        return new DataIntegrityViolationException("could not execute statement", new PSQLException(serverError));
    }
}
